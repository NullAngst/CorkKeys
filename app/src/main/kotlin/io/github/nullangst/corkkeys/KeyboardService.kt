/*
 * CorkKeys, a private keyboard for Android.
 * Copyright (C) 2026 CorkKeys contributors
 * Licensed under the GNU General Public License v3.0 or later. See LICENSE.
 */
package io.github.nullangst.corkkeys

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * The InputMethodService. Builds the view, reacts to a new field or a cursor move, and hands
 * the work to the files named for each feature. The only thing that touches the app is the
 * input connection Android hands the keyboard.
 *
 * The keyboard window covers the screen, so popups can rise above the keys and the floating
 * keyboard can move anywhere, but only the keyboard's own rectangle takes touches
 * ([onComputeInsets]); everything else goes straight to the app.
 */
class KeyboardService : InputMethodService(), TouchHost, SuggestionStrip.Host, ToolsRow.Host,
    EditingPanel.Host, LanguageMenu.Host, KeyAccessibility.Host {

    lateinit var prefs: Prefs
        private set
    var field: FieldInfo = FieldInfo.PLAIN
        private set
    val shift = Shift()
    private val composer = Composer(this)
    private lateinit var pal: Palette

    private enum class Page { BASE, SYMBOLS, SYMBOLS_MORE }

    private var page = Page.BASE
    private var editing = false

    private var root: Root? = null
    private var container: LinearLayout? = null
    private var keyArea: FrameLayout? = null
    private var keys: KeyboardView? = null
    private var strip: SuggestionStrip? = null
    private var tools: ToolsRow? = null
    private var panel: EditingPanel? = null
    private var menu: LanguageMenu? = null

    private var insetLeft = 0
    private var insetRight = 0
    private var insetBottom = 0

    private val main = Handler(Looper.getMainLooper())
    private var corrector: Corrector? = null
    private var correctorKey = ""
    private var suggestGen = 0

    /** The last suggestions computed: for which word, the candidates, and what autocorrect would do. */
    private var lastWord = ""
    private var lastCandidates: List<Candidate> = emptyList()
    private var lastAuto: String? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.open(this)
        pal = Themes.palette(this, prefs.theme)
    }

    // ---- Views ----

    /** The full-window root. Tells the service when its size changes (rotation, split screen). */
    @SuppressLint("ViewConstructor")
    private class Root(ctx: Context, val onSize: () -> Unit) : FrameLayout(ctx) {
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w != oldw || h != oldh) post { onSize() }
        }
    }

    override fun onCreateInputView(): View {
        val r = Root(this) { applyFrame() }
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val s = SuggestionStrip(this, this)
        val t = ToolsRow(this, this)
        s.toolsRow = t
        val area = FrameLayout(this)
        val kv = KeyboardView(this, this)
        kv.accessibility = KeyAccessibility(kv, this)
        val p = EditingPanel(this, this).apply { visibility = View.GONE }
        val m = LanguageMenu(this, this).apply { visibility = View.GONE }
        area.addView(kv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        area.addView(p, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        area.addView(m, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val stripH = (SuggestionStrip.HEIGHT_DP * resources.displayMetrics.density).toInt()
        c.addView(s, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, stripH))
        c.addView(area, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        r.addView(c, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        val overlay = KeyboardView.Overlay(this, kv)
        kv.overlay = overlay
        r.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        r.setOnApplyWindowInsetsListener { _, wi ->
            readInsets(wi)
            applyFrame()
            wi
        }
        root = r
        container = c
        keyArea = area
        keys = kv
        strip = s
        tools = t
        panel = p
        menu = m
        applyPalette()
        return r
    }

    private fun readInsets(wi: WindowInsets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val i = wi.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            insetLeft = i.left
            insetRight = i.right
            insetBottom = i.bottom
        } else {
            @Suppress("DEPRECATION")
            insetLeft = wi.systemWindowInsetLeft
            @Suppress("DEPRECATION")
            insetRight = wi.systemWindowInsetRight
            @Suppress("DEPRECATION")
            insetBottom = wi.systemWindowInsetBottom
        }
    }

    /**
     * Makes the keyboard window fill the screen with the keys at the bottom, drawn behind the
     * navigation bar. The insets listener pads the keys clear of it.
     */
    private fun fillWindow() {
        val w = window?.window ?: return
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        w.findViewById<View>(android.R.id.inputArea)?.let { area ->
            val lp = area.layoutParams
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                if (lp is FrameLayout.LayoutParams) lp.gravity = Gravity.BOTTOM
                if (lp is LinearLayout.LayoutParams) lp.gravity = Gravity.BOTTOM
                area.layoutParams = lp
            }
        }
        (root?.parent as? View)?.let { frame ->
            val lp = frame.layoutParams
            if (lp != null && lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                frame.layoutParams = lp
            }
        }
        root?.layoutParams?.let {
            if (it.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                it.height = ViewGroup.LayoutParams.MATCH_PARENT
                root?.layoutParams = it
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            w.setDecorFitsSystemWindows(false)
            val light = WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (pal.isDark) 0 else light, light)
        } else {
            @Suppress("DEPRECATION")
            w.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                (if (pal.isDark) 0 else View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        }
        @Suppress("DEPRECATION")
        w.navigationBarColor = Color.TRANSPARENT
        w.isNavigationBarContrastEnforced = false
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        fillWindow()
    }

    /** No full-screen typing box in landscape: the app stays visible above the keys. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun frame(): Frame {
        val dm = resources.displayMetrics
        val r = root
        val w = if (r != null && r.width > 0) r.width else dm.widthPixels
        val h = if (r != null && r.height > 0) r.height else dm.heightPixels
        return Frame(w, h, dm.density, insetLeft, insetRight, insetBottom)
    }

    private fun applyFrame() {
        val f = frame()
        keys?.setFrame(f)
        strip?.setPadding(insetLeft, 0, insetRight, 0)
        syncPanelHeight()
    }

    private fun syncPanelHeight() {
        val kv = keys ?: return
        val p = panel ?: return
        kv.post {
            val h = kv.plan?.height?.toInt() ?: return@post
            val lp = p.layoutParams
            if (lp.height != h) {
                lp.height = h
                p.layoutParams = lp
            }
            p.setPadding(insetLeft, p.paddingTop, insetRight, insetBottom + (3 * resources.displayMetrics.density).toInt())
        }
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        val r = root ?: return
        val c = container ?: return
        if (r.height == 0 || c.height == 0) return
        val top = c.top
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(c.left, c.top, c.right, c.bottom)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        pal = Themes.palette(this, prefs.theme)
    }

    // ---- A field opens, the cursor moves ----

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        field = Field.read(info)
        pal = Themes.palette(this, prefs.theme)
        fillWindow()
        applyPalette()
        if (!restarting) {
            page = Page.BASE
            shift.reset()
            closeEditing()
            closeMenu()
            strip?.setToolsOpen(false)
        }
        loadWords()
        composer.start(info?.initialSelStart ?: -1, info?.initialSelEnd ?: -1)
        Languages.announce(this, prefs.language)
        showLayout()
        refreshStrip()
        updateShift()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keys?.touch?.cancelAll()
        composer.finish()
        closeMenu()
        if (!prefs.learn || prefs.incognito) LearnedWords.get(this).forgetPending()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        composer.finish()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        composer.onSelection(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateShift()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val lang = Languages.of(newSubtype) ?: return
        if (lang in prefs.languages && lang != prefs.current) {
            prefs.current = lang
            showLayout()
        }
    }

    private fun applyPalette() {
        keys?.pal = pal
        keys?.bordered = prefs.bordered
        strip?.applyPalette(pal)
        tools?.applyPalette(pal)
        panel?.applyPalette(pal)
        container?.setBackgroundColor(pal.keyboardBg)
        keys?.invalidate()
    }

    private fun baseLayout(): Layout = Field.layoutFor(field.kind, prefs.language, prefs.numberRow)

    private fun letterRows(): Int = Letters.build(Letters.data(prefs.language), prefs.numberRow).rows.size

    private fun showLayout() {
        val kv = keys ?: return
        val lang = prefs.language
        val layout = when (page) {
            Page.BASE -> baseLayout()
            Page.SYMBOLS -> LayoutSymbols.page1(lang)
            Page.SYMBOLS_MORE -> LayoutSymbols.page2(lang)
        }
        val rows = when (field.kind) {
            FieldKind.TEXT, FieldKind.EMAIL, FieldKind.URL -> letterRows()
            else -> 4
        }
        kv.enter = field.enter
        kv.spaceLabel = spaceLabel()
        kv.setLayout(layout, frame(), prefs.height, Mode.DOCKED, rows)
        syncPanelHeight()
    }

    private fun spaceLabel(): String {
        val label = Languages.spaceLabel(prefs.language, prefs.languages, mixing())
        return if (prefs.incognito) label + " · " + getString(R.string.space_incognito) else label
    }

    /** Words from every language that is on, in one sentence. Only matters with suggestions on. */
    private fun mixing(): Boolean = prefs.twoAtOnce && prefs.suggestions && prefs.languages.size > 1

    private fun updateShift() {
        val kv = keys ?: return
        val onLetters = page == Page.BASE && (field.kind == FieldKind.TEXT || field.kind == FieldKind.EMAIL || field.kind == FieldKind.URL)
        if (onLetters && prefs.autoCaps && field.allowsAutoCaps) {
            val caps = currentInputConnection?.getCursorCapsMode(field.inputType) ?: 0
            shift.auto(caps != 0)
        } else {
            shift.auto(false)
        }
        if (kv.shift != shift.state) {
            kv.shift = shift.state
            kv.invalidate()
            keys?.accessibility?.invalidateAll()
        }
    }

    private fun refreshStrip() {
        wordChanged()
    }

    // ---- Words: suggestions, autocorrect, learning ----

    /** Suggestions are on, and this field allows them. */
    fun composing(): Boolean = prefs.suggestions && field.allowsSuggestions(prefs.inNoSuggestFields)

    fun autocorrectOn(): Boolean = composing() && prefs.autocorrect && field.allowsAutocorrect

    /** Languages whose lists the keyboard needs right now. None at all for a plain keyboard. */
    private fun wordLanguages(): List<String> =
        if (prefs.suggestions) Languages.mixed(prefs.language, prefs.languages, mixing()) else emptyList()

    private fun loadWords() {
        val need = wordLanguages()
        Dictionaries.keepOnly(need + SpellCheck.inUse)
        if (need.isEmpty()) return
        Dictionaries.load(this, need) {
            main.post {
                correctorKey = ""
                if (composer.word.isNotEmpty()) wordChanged()
            }
        }
    }

    /** The corrector for the languages in use, or null while their lists are still loading. */
    private fun corrector(): Corrector? {
        val langs = wordLanguages()
        if (langs.isEmpty()) return null
        val dicts = langs.mapNotNull { Dictionaries.get(it) }
        if (dicts.isEmpty() || dicts[0].language != prefs.language) return null
        val learned = LearnedWords.get(this)
        val removed = RemovedWords.get(this)
        val learnOn = prefs.learn
        val key = langs.joinToString(",") + "|" + dicts.size + "|" + learnOn + "|" + learned.version + "|" + removed.version
        if (key != correctorKey || corrector == null) {
            val keyMap = KeyMap.of(Letters.build(Letters.data(prefs.language), false))
            val mine = if (learnOn) learned.all().toList().takeIf { it.isNotEmpty() }?.let { Dictionary.ofWords("user", it, LEARNED_FREQUENCY) } else null
            corrector = Corrector(dicts, keyMap, removed.all().toSet(), mine)
            correctorKey = key
        }
        return corrector
    }

    /** The word being typed changed: work out its suggestions off the main thread. */
    fun wordChanged() {
        val s = strip ?: return
        val typed = composer.word.toString()
        val gen = ++suggestGen
        if (typed.isEmpty() || !composing()) {
            lastWord = ""
            s.show(emptyList(), -1)
            return
        }
        val c = corrector()
        if (c == null) {
            s.show(emptyList(), -1)
            return
        }
        val swear = prefs.swearWords
        val auto = autocorrectOn()
        val locked = shift.locked
        Dictionaries.onWorker {
            val cands = c.suggest(typed, swear)
            val fix = if (auto) c.autocorrection(typed, cands) else null
            main.post {
                if (gen != suggestGen) return@post
                lastWord = typed
                lastCandidates = cands
                lastAuto = fix
                showSuggestions(typed, cands, fix, locked)
            }
        }
    }

    private fun showSuggestions(typed: String, cands: List<Candidate>, fix: String?, locked: Boolean) {
        val words = ArrayList<String>(SuggestionStrip.SLOTS)
        words += typed
        for (cand in cands) {
            if (words.size == SuggestionStrip.SLOTS) break
            val w = Chars.applyCase(typed, cand.word, locked)
            if (words.none { it == w } && !cand.word.equals(typed, ignoreCase = false)) words += w
        }
        val highlight = if (fix == null) -1 else words.indexOf(Chars.applyCase(typed, fix, locked)).takeIf { it > 0 } ?: -1
        strip?.show(words, highlight)
    }

    /** What autocorrect would put in place of [typed], from the last suggestions when they match. */
    fun autocorrection(typed: String): String? {
        if (typed == lastWord) return lastAuto
        val c = corrector() ?: return null
        return c.autocorrection(typed, c.suggest(typed, prefs.swearWords))
    }

    /** A word went in exactly as tapped out. Learned only with the switch on, outside incognito and private fields. */
    fun learn(word: String) {
        val allowed = prefs.learn && !prefs.incognito && field.allowsLearning
        if (!allowed) return
        val c = corrector() ?: return
        if (LearnedWords.get(this).typedInFull(word, true, c.inList(word))) correctorKey = ""
    }

    // ---- Keys ----

    override fun pressed(k: PlacedKey, slid: Boolean) {
        val kv = keys ?: return
        if (!slid) Haptics.key(kv, prefs.vibrate)
        kv.pressed.clear()
        kv.pressed += k
        if (prefs.bubbles && field.allowsBubbles && k.key.kind == Kind.CHAR) {
            kv.preview.show(k, kv.labelOf(k.key))
        } else {
            kv.preview.hide()
        }
        kv.invalidate()
        kv.invalidateOverlay()
    }

    override fun released() {
        val kv = keys ?: return
        if (kv.pressed.isEmpty() && kv.preview.key == null) return
        kv.pressed.clear()
        kv.preview.hide()
        kv.invalidate()
        kv.invalidateOverlay()
    }

    override fun tap(k: PlacedKey) {
        val key = k.key
        when (key.kind) {
            Kind.CHAR -> type(if (key.isLetter && shift.upper) key.shifted() else key.output, key.isLetter)
            Kind.SPACE -> {
                composer.space(Touch.now())
                if (page != Page.BASE) toPage(Page.BASE)
            }
            Kind.ENTER -> {
                composer.enter()
                if (page != Page.BASE) toPage(Page.BASE)
            }
            Kind.BACKSPACE -> composer.backspace()
            Kind.SHIFT -> {
                shift.tap(Touch.now())
                keys?.shift = shift.state
                keys?.invalidate()
                keys?.accessibility?.invalidateAll()
                return
            }
            Kind.SYMBOLS -> toPage(Page.SYMBOLS)
            Kind.SYMBOLS_MORE -> toPage(Page.SYMBOLS_MORE)
            Kind.LETTERS -> toPage(Page.BASE)
            Kind.ONE_HANDED_FLIP, Kind.ONE_HANDED_EXIT -> {}
        }
        updateShift()
    }

    private fun type(text: String, letter: Boolean) {
        composer.text(text, shift.locked)
        if (letter) shift.typed()
    }

    private fun toPage(p: Page) {
        page = p
        showLayout()
        updateShift()
    }

    override fun popupPicked(text: String) {
        type(text, text.length == 1 && text[0].isLetter())
        updateShift()
    }

    override fun longPress(k: PlacedKey): Boolean {
        val kv = keys ?: return false
        return when {
            k.key.kind == Kind.SPACE -> {
                Haptics.longPress(kv, prefs.vibrate)
                openMenu()
                true
            }
            k.key.longPressSettings -> {
                Haptics.longPress(kv, prefs.vibrate)
                openSettings()
                true
            }
            else -> false
        }
    }

    override fun backspaceRepeat() {
        composer.backspace()
        updateShift()
    }

    override fun cursor(steps: Int) = composer.cursor(steps)

    override fun selectWords(words: Int) = composer.selectWords(words)

    override fun deleteSelectedWords() {
        composer.deleteSelectedWords()
        updateShift()
    }

    override fun swipeAllowed(): Boolean = false

    override fun swipeMoved(x: Float, y: Float) {}

    override fun swiped(xs: FloatArray, ys: FloatArray) {}

    // ---- Accessibility ----

    override fun describe(k: PlacedKey): String {
        val key = k.key
        return when (key.kind) {
            Kind.SHIFT -> getString(
                when (shift.state) {
                    Shift.State.OFF -> R.string.cd_shift
                    Shift.State.LOCK -> R.string.cd_caps_lock
                    else -> R.string.cd_shift_on
                },
            )
            Kind.BACKSPACE -> getString(R.string.cd_backspace)
            Kind.SPACE -> getString(R.string.cd_space) + ", " + keys?.spaceLabel.orEmpty()
            Kind.ENTER -> getString(
                when (field.enter) {
                    Enter.SEARCH -> R.string.cd_search
                    Enter.SEND -> R.string.cd_send
                    Enter.NEXT -> R.string.cd_next
                    Enter.DONE -> R.string.cd_done
                    Enter.GO -> R.string.cd_go
                    Enter.PREVIOUS -> R.string.cd_previous
                    Enter.NEWLINE -> R.string.cd_enter
                },
            )
            Kind.SYMBOLS -> getString(R.string.cd_symbols)
            Kind.SYMBOLS_MORE -> getString(R.string.cd_more_symbols)
            Kind.LETTERS -> getString(R.string.cd_letters)
            Kind.ONE_HANDED_FLIP -> getString(R.string.cd_flip)
            Kind.ONE_HANDED_EXIT -> getString(R.string.cd_one_handed_off)
            Kind.CHAR -> when (key.output) {
                "," -> getString(R.string.cd_comma)
                "." -> getString(R.string.cd_period)
                else -> keys?.labelOf(key) ?: key.label
            }
        }
    }

    override fun typeFromAccessibility(k: PlacedKey) {
        keys?.let { Haptics.key(it, prefs.vibrate) }
        tap(k)
    }

    // ---- Strip, tools, editing panel, language menu ----

    override fun buttonFeedback(v: View) = Haptics.key(v, prefs.vibrate)

    override fun toolsPressed() {
        val s = strip ?: return
        s.setToolsOpen(!s.toolsOpen)
    }

    override fun pastePressed() = Paste.paste(this, currentInputConnection)

    override fun picked(slot: Int, word: String) {
        composer.pick(word, slot)
        updateShift()
    }

    /** Long-press on a suggestion: never suggest it again. */
    override fun removeAsked(word: String) {
        keys?.let { Haptics.longPress(it, prefs.vibrate) }
        RemovedWords.get(this).add(word)
        correctorKey = ""
        wordChanged()
    }

    override fun tool(t: ToolsRow.Tool, v: View) {
        buttonFeedback(v)
        when (t) {
            ToolsRow.Tool.EDIT -> openEditing()
            ToolsRow.Tool.SETTINGS -> openSettings()
        }
        strip?.setToolsOpen(false)
    }

    private fun openEditing() {
        val p = panel ?: return
        editing = true
        p.reset()
        syncPanelHeight()
        p.visibility = View.VISIBLE
        keys?.visibility = View.INVISIBLE
    }

    private fun closeEditing() {
        editing = false
        panel?.visibility = View.GONE
        keys?.visibility = View.VISIBLE
    }

    override fun edit(a: EditingPanel.Action, select: Boolean, v: View) {
        Haptics.key(v, prefs.vibrate)
        val ic = currentInputConnection
        when (a) {
            EditingPanel.Action.LEFT -> composer.key(KeyEvent.KEYCODE_DPAD_LEFT, select)
            EditingPanel.Action.RIGHT -> composer.key(KeyEvent.KEYCODE_DPAD_RIGHT, select)
            EditingPanel.Action.UP -> composer.key(KeyEvent.KEYCODE_DPAD_UP, select)
            EditingPanel.Action.DOWN -> composer.key(KeyEvent.KEYCODE_DPAD_DOWN, select)
            EditingPanel.Action.HOME -> composer.key(KeyEvent.KEYCODE_MOVE_HOME, select)
            EditingPanel.Action.END -> composer.key(KeyEvent.KEYCODE_MOVE_END, select)
            EditingPanel.Action.SELECT -> {}
            EditingPanel.Action.SELECT_ALL -> ic?.performContextMenuAction(android.R.id.selectAll)
            EditingPanel.Action.CUT -> ic?.performContextMenuAction(android.R.id.cut)
            EditingPanel.Action.COPY -> ic?.performContextMenuAction(android.R.id.copy)
            EditingPanel.Action.PASTE -> Paste.paste(this, ic)
            EditingPanel.Action.BACKSPACE -> composer.backspace()
            EditingPanel.Action.CLOSE -> closeEditing()
        }
    }

    private fun openMenu() {
        val m = menu ?: return
        keys?.touch?.cancelAll()
        m.fill(pal, prefs.languages, prefs.language, prefs.incognito)
        m.visibility = View.VISIBLE
    }

    private fun closeMenu() {
        menu?.visibility = View.GONE
    }

    override fun menuClosed() = closeMenu()

    override fun languagePicked(lang: String) {
        prefs.current = lang
        closeMenu()
        Languages.announce(this, lang)
        showLayout()
        updateShift()
    }

    override fun incognitoToggled(on: Boolean) {
        prefs.incognito = on
        closeMenu()
        keys?.spaceLabel = spaceLabel()
        keys?.invalidate()
    }

    private fun openSettings() {
        try {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        } catch (_: Exception) {
        }
        requestHideSelf(0)
    }

    companion object {
        /** Learned words count as real words, about as common as an everyday word. */
        const val LEARNED_FREQUENCY = 120
    }
}
