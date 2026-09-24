package com.typingpet.app

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo

class TypingPetIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var petView: PetView
    private lateinit var keyboard: Keyboard
    private var isShifted = false

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_container, null)
        petView = root.findViewById(R.id.petView)
        keyboardView = root.findViewById(R.id.keyboardView)

        keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.invalidateAllKeys()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_SHIFT -> {
                isShifted = !isShifted
                keyboard.isShifted = isShifted
                keyboardView.invalidateAllKeys()
                return // シフトそのものは打鍵アニメーションを起こさない
            }
            -2 -> {
                // 記号面への切り替えは今回のシンプル版では未実装
            }
            -4 -> ic.commitText("\n", 1)
            else -> {
                var code = primaryCode.toChar()
                if (isShifted) code = code.uppercaseChar()
                ic.commitText(code.toString(), 1)
            }
        }

        petView.react()
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
