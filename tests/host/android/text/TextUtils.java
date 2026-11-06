package android.text;

/** Only the platform text utility used during libsu shell startup on a host JVM.
 * No libsu classes, jobs, streams or results are replaced by this shim. */
public final class TextUtils {
    public static boolean isEmpty(CharSequence value) { return value == null || value.length() == 0; }
}
