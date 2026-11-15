package org.telegram.messenger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards the R8 rules that a release build needs and a debug build never exercises.
 * Reads the sources and proguard-rules.pro from disk (Gradle runs unit tests with the module as working dir).
 */
public class NanogramR8KeepRulesTest {

    private static final String JAVA_ROOT = "src/main/java";

    private static File moduleFile(String rel) {
        File f = new File(rel);
        if (!f.exists()) {
            f = new File("TMessagesProj/" + rel);
        }
        return f;
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String rules() throws Exception {
        return read(moduleFile("proguard-rules.pro"));
    }

    @Test
    public void translateJniIsKept() throws Exception {
        String r = rules();
        assertTrue(r.contains("-keep class com.google.mlkit.nl.translate.internal.TranslateJni { *; }"));
        assertTrue(r.contains("-keep class com.google.mlkit.nl.languageid.internal.LanguageIdentificationJni { *; }"));
    }

    @Test
    public void broadFieldNameRuleStillThere() throws Exception {
        assertTrue(rules().contains("-keepclassmembernames,allowoptimization class org.telegram.ui.** {"));
    }

    /** Every (class, field) a ThemeDescription names must have an exact -keepclassmembers rule. */
    @Test
    public void everyThemeDescriptionFieldHasAKeepRule() throws Exception {
        String r = rules();
        int b = r.indexOf("# BEGIN theme-description-fields");
        int e = r.indexOf("# END theme-description-fields");
        assertTrue("marker block missing", b >= 0 && e > b);
        // simple class name (after the last '.' or '$') -> kept field names
        Map<String, TreeSet<String>> kept = new HashMap<>();
        Matcher cm = Pattern.compile("-keepclassmembers class ([\\w.$]+) \\{([^}]*)\\}").matcher(r.substring(b, e));
        while (cm.find()) {
            String fqn = cm.group(1);
            String simple = fqn.substring(Math.max(fqn.lastIndexOf('.'), fqn.lastIndexOf('$')) + 1);
            TreeSet<String> set = kept.get(simple);
            if (set == null) {
                set = new TreeSet<>();
                kept.put(simple, set);
            }
            Matcher fm = Pattern.compile("\\*\\*\\* (\\w+);").matcher(cm.group(2));
            while (fm.find()) {
                set.add(fm.group(1));
            }
        }
        assertTrue("no rules parsed", kept.size() > 20);
        // each rule's full class name must resolve to a source file (R8 silently ignores a rule that matches nothing)
        Matcher fq = Pattern.compile("-keepclassmembers class ([\\w.$]+) \\{").matcher(r.substring(b, e));
        while (fq.find()) {
            String fqn = fq.group(1);
            String outer = fqn.contains("$") ? fqn.substring(0, fqn.indexOf('$')) : fqn;
            File src = moduleFile(JAVA_ROOT + "/" + outer.replace('.', '/') + ".java");
            assertTrue("rule class has no source file: " + fqn, src.exists());
            String simple = fqn.substring(Math.max(fqn.lastIndexOf('.'), fqn.lastIndexOf('$')) + 1);
            assertTrue("rule class not declared in " + src + ": " + fqn, read(src).contains("class " + simple));
        }

        List<File> sources = new ArrayList<>();
        collect(moduleFile(JAVA_ROOT), sources);
        assertTrue("sources not found from " + new File(".").getAbsolutePath(), sources.size() > 1000);

        List<String> missing = new ArrayList<>();
        int pairs = 0;
        Pattern call = Pattern.compile("new ThemeDescription\\(");
        Pattern lists = Pattern.compile("new Class\\[\\]\\s*\\{([^}]*)\\}\\s*,\\s*new String\\[\\]\\s*\\{([^}]*)\\}");
        for (File f : sources) {
            String s = read(f);
            if (!s.contains("new ThemeDescription(")) {
                continue;
            }
            Matcher m = call.matcher(s);
            while (m.find()) {
                int i = m.end();
                int depth = 1;
                while (depth > 0 && i < s.length()) {
                    char c = s.charAt(i++);
                    if (c == '(') depth++;
                    else if (c == ')') depth--;
                }
                Matcher lm = lists.matcher(s.substring(m.end(), i));
                if (!lm.find()) {
                    continue;
                }
                List<String> classes = new ArrayList<>();
                Matcher cl = Pattern.compile("([\\w.]+)\\.class").matcher(lm.group(1));
                while (cl.find()) {
                    String n = cl.group(1);
                    classes.add(n.substring(n.lastIndexOf('.') + 1));
                }
                Matcher fl = Pattern.compile("\"([^\"]+)\"").matcher(lm.group(2));
                while (fl.find()) {
                    for (String c : classes) {
                        if (!declaresField(c, fl.group(1), sources)) {
                            continue; // stale upstream descriptor: the field does not exist, nothing to keep
                        }
                        pairs++;
                        TreeSet<String> set = kept.get(c);
                        if (set == null || !set.contains(fl.group(1))) {
                            missing.add(c + "." + fl.group(1));
                        }
                    }
                }
            }
        }
        assertTrue("pairs found: " + pairs, pairs > 40);
        assertTrue("ThemeDescription fields without a -keepclassmembers rule: " + missing, missing.isEmpty());
    }

    @Test
    public void ruleFilePresentAndNoUnsupportedTypedFieldSyntax() throws Exception {
        // R8 rejected "View+ *;" style rules ("Expected field or method name"): keep to "*** name;".
        assertFalse(rules().contains("View+ *;"));
    }

    private static final Map<String, String> SRC_CACHE = new HashMap<>();

    private static boolean declaresField(String simpleClass, String field, List<File> sources) throws Exception {
        Pattern decl = Pattern.compile("(?:[\\w>\\]]\\s+|,\\s*)" + Pattern.quote(field) + "\\s*(=|;|,)");
        Pattern cls = Pattern.compile("class " + Pattern.quote(simpleClass) + "\\b");
        for (File f : sources) {
            if (!f.getPath().contains("/org/telegram/ui/")) {
                continue;
            }
            String s = SRC_CACHE.get(f.getPath());
            if (s == null) {
                s = read(f);
                SRC_CACHE.put(f.getPath(), s);
            }
            if (cls.matcher(s).find()) {
                if (decl.matcher(s).find()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collect(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) {
            return;
        }
        for (File f : list) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (f.getName().endsWith(".java")) {
                out.add(f);
            }
        }
    }
}
