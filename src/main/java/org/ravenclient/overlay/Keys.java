package org.ravenclient.overlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps Windows virtual-key codes to display names and back. Used for the
 * configurable menu key and per-module keybinds (keyboard + mouse buttons).
 */
public final class Keys {

    public static final int VK_LBUTTON = 0x01;
    public static final int VK_RBUTTON = 0x02;
    public static final int VK_MBUTTON = 0x04;
    public static final int VK_RSHIFT = 0xA1;

    private static final Map<Integer, String> NAME = new LinkedHashMap<>();
    private static final Map<String, Integer> CODE = new HashMapIgnoreCase();

    static {
        // Mouse buttons
        register(VK_LBUTTON, "Left Click");
        register(VK_RBUTTON, "Right Click");
        register(VK_MBUTTON, "Middle Click");

        // Common keys
        register(0x08, "Backspace");
        register(0x09, "Tab");
        register(0x0D, "Enter");
        register(0x10, "Shift");
        register(0x11, "Ctrl");
        register(0x12, "Alt");
        register(0x14, "Caps Lock");
        register(0x1B, "Esc");
        register(0x20, "Space");
        register(0x21, "Page Up");
        register(0x22, "Page Down");
        register(0x23, "End");
        register(0x24, "Home");
        register(0x25, "Left");
        register(0x26, "Up");
        register(0x27, "Right");
        register(0x28, "Down");
        register(0x2D, "Insert");
        register(0x2E, "Delete");

        // Digits 0-9
        for (int i = 0; i <= 9; i++) register(0x30 + i, String.valueOf(i));
        // Letters A-Z
        for (int i = 0; i < 26; i++) register(0x41 + i, String.valueOf((char) ('A' + i)));
        // Numpad 0-9
        for (int i = 0; i <= 9; i++) register(0x60 + i, "Num " + i);
        // Function keys
        for (int i = 1; i <= 12; i++) register(0x70 + i - 1, "F" + i);
        // Modifier variants
        register(0xA0, "Left Shift");
        register(0xA1, "Right Shift");
        register(0xA2, "Left Ctrl");
        register(0xA3, "Right Ctrl");
        register(0xA4, "Left Alt");
        register(0xA5, "Right Alt");
        // Punctuation
        register(0xBA, ";");
        register(0xBB, "=");
        register(0xBC, ",");
        register(0xBD, "-");
        register(0xBE, ".");
        register(0xBF, "/");
        register(0xC0, "`");
        register(0xDB, "[");
        register(0xDC, "\\");
        register(0xDD, "]");
        register(0xDE, "'");
    }

    private static void register(int vk, String name) {
        NAME.put(vk, name);
        CODE.put(name, vk);
    }

    /** Display name for a VK code, e.g. 0xA1 -> "Right Shift". */
    public static String name(int vk) {
        return NAME.getOrDefault(vk, "Key 0x" + Integer.toHexString(vk).toUpperCase(Locale.ROOT));
    }

    /** VK code for a stored keybind name, or -1 if unknown. */
    public static int code(String name) {
        if (name == null) return -1;
        Integer vk = CODE.get(name.trim());
        return vk == null ? -1 : vk;
    }

    /** All bindable VK codes, in display order. */
    public static List<Integer> bindable() {
        return new ArrayList<>(NAME.keySet());
    }

    /** Converts a JavaFX KeyCode into a Windows VK code (-1 if not bindable). */
    public static int vkForFx(javafx.scene.input.KeyCode kc) {
        if (kc == null) return -1;
        String n = kc.getName();
        Integer vk = CODE.get(n);
        if (vk != null) return vk;
        // JavaFX names that don't match our display names directly
        switch (kc) {
            case ESCAPE: return 0x1B;
            case BACK_SPACE: return 0x08;
            case DELETE: return 0x2E;
            case PAGE_UP: return 0x21;
            case PAGE_DOWN: return 0x22;
            case INSERT: return 0x2D;
            case ENTER: return 0x0D;
            case SPACE: return 0x20;
            case TAB: return 0x09;
            case CAPS: return 0x14;
            case HOME: return 0x24;
            case END: return 0x23;
            case UP: return 0x26;
            case DOWN: return 0x28;
            case LEFT: return 0x25;
            case RIGHT: return 0x27;
            case SHIFT: return 0x10;
            case CONTROL: return 0x11;
            case ALT: return 0x12;
            default: {
                // Digits, letters and F-keys
                if (n.length() == 1) {
                    char c = n.charAt(0);
                    if (c >= 'A' && c <= 'Z') return 0x41 + (c - 'A');
                    if (c >= '0' && c <= '9') return 0x30 + (c - '0');
                } else if (n.length() == 2 && n.charAt(0) == 'F') {
                    try {
                        int f = Integer.parseInt(n.substring(1));
                        if (f >= 1 && f <= 12) return 0x70 + f - 1;
                    } catch (NumberFormatException ignored) {}
                }
                return -1;
            }
        }
    }

    /** Case-insensitive String -> Integer map. */
    private static final class HashMapIgnoreCase extends LinkedHashMap<String, Integer> {
        @Override
        public Integer put(String key, Integer value) {
            return super.put(key == null ? null : key.toLowerCase(Locale.ROOT), value);
        }

        @Override
        public Integer get(Object key) {
            return super.get(key == null ? null : key.toString().toLowerCase(Locale.ROOT));
        }
    }

    private Keys() {}
}
