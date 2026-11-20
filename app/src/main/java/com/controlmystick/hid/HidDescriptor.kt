package com.controlmystick.hid

/**
 * Descriptor HID combinado: teclado (Report ID 1) + Consumer Control (Report ID 2).
 *
 * Android TV traduce:
 *  - Flechas del teclado -> DPAD_UP/DOWN/LEFT/RIGHT
 *  - Enter -> DPAD_CENTER (OK)
 *  - Usos de Consumer (Home, Back, Volumen...) -> sus KEYCODE equivalentes
 */
object HidDescriptor {

    const val REPORT_ID_KEYBOARD = 1
    const val REPORT_ID_CONSUMER = 2

    const val KEYBOARD_REPORT_SIZE = 8
    const val CONSUMER_REPORT_SIZE = 2

    val DESCRIPTOR: ByteArray = bytes(
        // ---- Teclado ----
        0x05, 0x01,       // Usage Page (Generic Desktop)
        0x09, 0x06,       // Usage (Keyboard)
        0xA1, 0x01,       // Collection (Application)
        0x85, REPORT_ID_KEYBOARD, // Report ID (1)
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0xE0,       //   Usage Minimum (Left Control)
        0x29, 0xE7,       //   Usage Maximum (Right GUI)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x01,       //   Logical Maximum (1)
        0x75, 0x01,       //   Report Size (1)
        0x95, 0x08,       //   Report Count (8)
        0x81, 0x02,       //   Input (Data, Var, Abs) -> modificadores
        0x95, 0x01,       //   Report Count (1)
        0x75, 0x08,       //   Report Size (8)
        0x81, 0x01,       //   Input (Const) -> byte reservado
        0x95, 0x06,       //   Report Count (6)
        0x75, 0x08,       //   Report Size (8)
        0x15, 0x00,       //   Logical Minimum (0)
        0x25, 0x65,       //   Logical Maximum (101)
        0x05, 0x07,       //   Usage Page (Keyboard/Keypad)
        0x19, 0x00,       //   Usage Minimum (0)
        0x29, 0x65,       //   Usage Maximum (101)
        0x81, 0x00,       //   Input (Data, Array) -> 6 teclas
        0xC0,             // End Collection

        // ---- Consumer Control ----
        0x05, 0x0C,       // Usage Page (Consumer)
        0x09, 0x01,       // Usage (Consumer Control)
        0xA1, 0x01,       // Collection (Application)
        0x85, REPORT_ID_CONSUMER, // Report ID (2)
        0x15, 0x00,       //   Logical Minimum (0)
        0x26, 0xFF, 0x03, //   Logical Maximum (0x3FF)
        0x19, 0x00,       //   Usage Minimum (0)
        0x2A, 0xFF, 0x03, //   Usage Maximum (0x3FF)
        0x75, 0x10,       //   Report Size (16)
        0x95, 0x01,       //   Report Count (1)
        0x81, 0x00,       //   Input (Data, Array)
        0xC0              // End Collection
    )

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
}

/** Usages de la página Keyboard (0x07). */
object Key {
    const val ENTER = 0x28
    const val ESC = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52

    const val MOD_LEFT_SHIFT = 0x02
}

/** Usages de la página Consumer (0x0C). */
object Consumer {
    const val POWER = 0x30
    const val MENU = 0x40
    const val FAST_FORWARD = 0xB3
    const val REWIND = 0xB4
    const val NEXT_TRACK = 0xB5
    const val PREV_TRACK = 0xB6
    const val PLAY_PAUSE = 0xCD
    const val MUTE = 0xE2
    const val VOLUME_UP = 0xE9
    const val VOLUME_DOWN = 0xEA
    const val SEARCH = 0x221
    const val HOME = 0x223
    const val BACK = 0x224
}
