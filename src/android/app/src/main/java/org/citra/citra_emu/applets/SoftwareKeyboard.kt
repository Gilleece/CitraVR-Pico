// Copyright 2023 Citra Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package org.citra.citra_emu.applets

import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.Keep
import org.citra.citra_emu.CitraApplication.Companion.appContext
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import org.citra.citra_emu.fragments.KeyboardDialogFragment
import org.citra.citra_emu.utils.Log
import org.citra.citra_emu.vr.VrActivity
import org.citra.citra_emu.vr.ui.VrKeyboardView
import org.citra.citra_emu.vr.utils.VRUtils
import org.citra.citra_emu.vr.utils.VrMessageQueue
import java.io.Serializable


@Keep
object SoftwareKeyboard {
    lateinit var data: KeyboardData
    val finishLock = Object()

    // Shows an EditText anchored to the bottom of VrActivity's existing window.
    // No new Android Window is created, so the Pico VR container keeps focus and
    // the headset stays awake. The Pico system IME appears as a floating 3D panel.
    // The user presses the IME's Done action (or Back) to confirm/cancel.
    private fun showPicoKeyboardInput(activity: android.app.Activity, config: KeyboardConfig) {
        val rootView = activity.window.decorView as ViewGroup

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            setBackgroundColor(0xDD1A1A2E.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        val editText = EditText(activity).apply {
            hint = config.hintText ?: ""
            isSingleLine = !config.multilineMode
            filters = arrayOf(Filter(), InputFilter.LengthFilter(config.maxTextLength))
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(editText)
        rootView.addView(container)

        fun finish(button: Int, text: String) {
            rootView.removeView(container)
            data = KeyboardData(button, text)
            synchronized(finishLock) { finishLock.notifyAll() }
        }

        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                val text = editText.text.toString()
                val error = ValidateInput(text)
                if (error == ValidationError.None) {
                    finish(config.buttonConfig, text)
                    true
                } else {
                    HandleValidationError(config, error)
                    false
                }
            } else false
        }

        // Back key = cancel
        editText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                finish(0, "")
                true
            } else false
        }

        editText.requestFocus()
    }

    private fun ExecuteImpl(config: KeyboardConfig) {
        val emulationActivity = NativeLibrary.sEmulationActivity.get()
        data = KeyboardData(0, "")
        KeyboardDialogFragment.newInstance(config)
            .show(emulationActivity!!.supportFragmentManager, KeyboardDialogFragment.TAG)
    }

    fun HandleValidationError(config: KeyboardConfig, error: ValidationError) {
        val emulationActivity = NativeLibrary.sEmulationActivity.get()!!
        val message: String = when (error) {
            ValidationError.FixedLengthRequired -> emulationActivity.getString(
                R.string.fixed_length_required,
                config.maxTextLength
            )

            ValidationError.MaxLengthExceeded ->
                emulationActivity.getString(R.string.max_length_exceeded, config.maxTextLength)

            ValidationError.BlankInputNotAllowed ->
                emulationActivity.getString(R.string.blank_input_not_allowed)

            ValidationError.EmptyInputNotAllowed ->
                emulationActivity.getString(R.string.empty_input_not_allowed)

            else -> emulationActivity.getString(R.string.invalid_input)
        }

      /*  MessageDialogFragment.newInstance(R.string.software_keyboard, message).show(
            NativeLibrary.sEmulationActivity.get()!!.supportFragmentManager,
            MessageDialogFragment.TAG
        )*/
    }

    @JvmStatic
    fun Execute(config: KeyboardConfig): KeyboardData {
        if (config.buttonConfig == ButtonConfig.None) {
            Log.error("Unexpected button config None")
            return KeyboardData(0, "")
        }

        val emulationActivity = NativeLibrary.sEmulationActivity.get()
        val hmdType = VRUtils.hMDType
        val isPico = hmdType == VRUtils.HMDType.PICO4.value ||
                     hmdType == VRUtils.HMDType.PICO4ULTRA.value ||
                     hmdType == VRUtils.HMDType.FALLBACK_HMD.value

        // On Pico: add EditText directly to VrActivity's existing window (no new dialog window).
        // This keeps the VR container focused so the headset doesn't sleep. The Pico system IME
        // appears as its normal floating 3D keyboard panel. User presses Done to confirm.
        if (isPico) {
            Log.info("[SoftwareKeyboard] Pico: showing in-window keyboard input")
            data = KeyboardData(0, "") // default if timeout occurs
            NativeLibrary.sEmulationActivity.get()!!.runOnUiThread {
                showPicoKeyboardInput(emulationActivity!!, config)
            }
            synchronized(finishLock) {
                try {
                    finishLock.wait(120_000) // 2-minute timeout
                } catch (ignored: Exception) {}
            }
            return data
        }

        if (emulationActivity is VrActivity) {
            Log.info("[SoftwareKeyboard] VR keyboard requested (buttonConfig=${config.buttonConfig})")
            NativeLibrary.sEmulationActivity.get()!!.runOnUiThread {
                val keyboardView = VrKeyboardView.sVrKeyboardView.get()
                if (keyboardView == null) {
                    Log.error("[SoftwareKeyboard] VrKeyboardView not ready, cannot show keyboard")
                    synchronized(finishLock) { finishLock.notifyAll() }
                    return@runOnUiThread
                }
                Log.info("[SoftwareKeyboard] posting SHOW_KEYBOARD=1")
                keyboardView.setConfig(config)
                VrMessageQueue.post(VrMessageQueue.MessageType.SHOW_KEYBOARD, 1)
            }
        } else {
            Log.debug("Starting keyboard: non-VR")
            NativeLibrary.sEmulationActivity.get()!!.runOnUiThread { ExecuteImpl(config) }
        }
        Log.info("[SoftwareKeyboard] waiting on finishLock")
        synchronized(finishLock) {
            try {
                finishLock.wait(60_000)
            } catch (ignored: Exception) {
                Log.error("[SoftwareKeyboard] finishLock.wait() interrupted: ${ignored.message}")
            }
        }
        Log.info("[SoftwareKeyboard] finishLock released, button=${data.button} text=\"${data.text}\"")
        if (emulationActivity is VrActivity) {
            VrMessageQueue.post(VrMessageQueue.MessageType.SHOW_KEYBOARD, 0)
        }
        return data
    }

    @JvmStatic
    fun ShowError(error: String) {
        NativeLibrary.displayAlertMsg(
            appContext.resources.getString(R.string.software_keyboard),
            error,
            false
        )
    }

    private external fun ValidateFilters(text: String): ValidationError
    external fun ValidateInput(text: String): ValidationError

    /// Corresponds to Frontend::ButtonConfig
    interface ButtonConfig {
        companion object {
            const val Single = 0 /// Ok button
            const val Dual = 1 /// Cancel | Ok buttons
            const val Triple = 2 /// Cancel | I Forgot | Ok buttons
            const val None = 3 /// No button (returned by swkbdInputText in special cases)
        }
    }

    /// Corresponds to Frontend::ValidationError
    enum class ValidationError {
        None,

        // Button Selection
        ButtonOutOfRange,

        // Configured Filters
        MaxDigitsExceeded,
        AtSignNotAllowed,
        PercentNotAllowed,
        BackslashNotAllowed,
        ProfanityNotAllowed,
        CallbackFailed,

        // Allowed Input Type
        FixedLengthRequired,
        MaxLengthExceeded,
        BlankInputNotAllowed,
        EmptyInputNotAllowed
    }

    @Keep
    open class KeyboardConfig : Serializable {
        var buttonConfig = 0
        var maxTextLength = 0

        // True if the keyboard accepts multiple lines of input
        var multilineMode = false

        // Displayed in the field as a hint before
        var hintText: String? = null

        // Contains the button text that the caller provides
        lateinit var buttonText: Array<String>
    }

    /// Corresponds to Frontend::KeyboardData
    class KeyboardData(var button: Int, var text: String)
    class Filter : InputFilter {
        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            val text = StringBuilder(dest)
                .replace(dstart, dend, source.subSequence(start, end).toString())
                .toString()
            return if (ValidateFilters(text) == ValidationError.None) {
                null // Accept replacement
            } else {
                dest.subSequence(dstart, dend) // Request the subsequence to be unchanged
            }
        }
    }
    fun onFinishVrKeyboardPositive(text: String?, config: KeyboardConfig?) {
        Log.debug("[SoftwareKeyboard] button positive: \"$text\" config button: ${config!!.buttonConfig}")
        data = KeyboardData(config!!.buttonConfig, text!!)
        val error = ValidateInput(data.text)
        if (error != ValidationError.None) {
            HandleValidationError(config, error)
            onFinishVrKeyboardNegative()
            return
        }
        synchronized(finishLock) { finishLock.notifyAll() }
    }

    fun onFinishVrKeyboardNeutral() {
        Log.debug("[SoftwareKeyboard] button neutral")
        data = KeyboardData(1, "")
        synchronized(finishLock) { finishLock.notifyAll() }
    }

    fun onFinishVrKeyboardNegative() {
        Log.debug("[SoftwareKeyboard] button negative")
        data = KeyboardData(0, "")
        synchronized(finishLock) { finishLock.notifyAll() }
    }
}
