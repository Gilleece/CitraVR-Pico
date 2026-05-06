package org.citra.citra_emu.vr.ui

import android.view.View
import android.view.WindowManager
import android.widget.EditText
import org.citra.citra_emu.R
import org.citra.citra_emu.vr.VrActivity

class VrKeyboardLayer(activity: VrActivity) : VrUILayer(activity, R.layout.vr_keyboard) {

    override fun onSurfaceCreated() {
        super.onSurfaceCreated()
        // Suppress the system IME at window and view level.
        // On Pico's VR runtime, showing the system soft keyboard crashes the app.
        // Do NOT call requestFocus() on the EditText — it triggers the system IME on Pico
        // even with all suppression flags set. VrKeyboardView handles input via touch listeners.
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val editText = window?.findViewById<View>(R.id.vrKeyboardText) as EditText
        editText.setShowSoftInputOnFocus(false)
    }
}