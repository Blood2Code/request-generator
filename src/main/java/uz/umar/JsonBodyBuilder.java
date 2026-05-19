package uz.umar;

import com.intellij.psi.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JsonBodyBuilder {

    private static final String TODAY = LocalDate.now().toString();

    public static String build(PsiType type) {
        return buildValue(type, 0, new HashSet<>());
    }

    private static String buildValue(PsiType type, int depth, Set<String> visited) {
        if (depth > 5) return "null";

        if (type instanceof PsiPrimitiveType) {
            return primitiveDefault(type.getCanonicalText());
        }

        if (type instanceof PsiArrayType) {
            return "[]";
        }

        if (type instanceof PsiClassType classType) {
            PsiClass cls = classType.resolve();
            if (cls == null) return "null";

            String fqn = cls.getQualifiedName();
            if (fqn == null) return "null";

            if (isString(fqn))        return "\"\"";
            if (isInteger(fqn))       return "0";
            if (isDecimal(fqn))       return "0.0";
            if (isBoolean(fqn))       return "false";
            if (isLocalDate(fqn))     return "\"" + TODAY + "\"";
            if (isLocalDateTime(fqn)) return "\"" + TODAY + "T00:00:00\"";
            if (isList(fqn))          return "[]";
            if (isMap(fqn))           return "{}";
            if (cls.isEnum())         return "\"" + firstEnumConstant(cls) + "\"";

            if (visited.contains(fqn)) return "{}";
            visited.add(fqn);
            return buildObject(cls, depth, visited);
        }

        return "null";
    }

    private static String buildObject(PsiClass cls, int depth, Set<String> visited) {
        List<PsiField> fields = serializableFields(cls);
        if (fields.isEmpty()) return "{}";

        String indent = "  ".repeat(depth + 1);
        String closing = "  ".repeat(depth);
        StringBuilder sb = new StringBuilder("{\n");

        for (int i = 0; i < fields.size(); i++) {
            PsiField field = fields.get(i);
            String value = buildValue(field.getType(), depth + 1, new HashSet<>(visited));
            sb.append(indent).append("\"").append(field.getName()).append("\": ").append(value);
            if (i < fields.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append(closing).append("}");
        return sb.toString();
    }

    private static List<PsiField> serializableFields(PsiClass cls) {
        List<PsiField> result = new ArrayList<>();
        for (PsiField field : cls.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            if (field.hasModifierProperty(PsiModifier.TRANSIENT)) continue;
            if ("serialVersionUID".equals(field.getName())) continue;
            if (field.getAnnotation("com.fasterxml.jackson.annotation.JsonIgnore") != null) continue;
            result.add(field);
        }
        return result;
    }

    private static String primitiveDefault(String type) {
        return switch (type) {
            case "int", "long", "short", "byte" -> "0";
            case "float", "double" -> "0.0";
            case "boolean" -> "false";
            case "char" -> "\"\"";
            default -> "null";
        };
    }

    private static boolean isString(String fqn) {
        return "java.lang.String".equals(fqn) || "java.lang.Character".equals(fqn);
    }

    private static boolean isInteger(String fqn) {
        return switch (fqn) {
            case "java.lang.Integer", "java.lang.Long", "java.lang.Short",
                 "java.lang.Byte", "java.math.BigInteger" -> true;
            default -> false;
        };
    }

    private static boolean isDecimal(String fqn) {
        return switch (fqn) {
            case "java.lang.Double", "java.lang.Float", "java.math.BigDecimal" -> true;
            default -> false;
        };
    }

    private static boolean isBoolean(String fqn) {
        return "java.lang.Boolean".equals(fqn);
    }

    private static boolean isLocalDate(String fqn) {
        return switch (fqn) {
            case "java.time.LocalDate", "java.util.Date", "java.sql.Date" -> true;
            default -> false;
        };
    }

    private static boolean isLocalDateTime(String fqn) {
        return switch (fqn) {
            case "java.time.LocalDateTime", "java.time.ZonedDateTime",
                 "java.time.OffsetDateTime", "java.time.Instant" -> true;
            default -> false;
        };
    }

    private static boolean isList(String fqn) {
        return fqn.startsWith("java.util.List") || fqn.startsWith("java.util.ArrayList") ||
               fqn.startsWith("java.util.LinkedList") || fqn.startsWith("java.util.Set") ||
               fqn.startsWith("java.util.HashSet") || fqn.startsWith("java.util.Collection");
    }

    private static boolean isMap(String fqn) {
        return fqn.startsWith("java.util.Map") || fqn.startsWith("java.util.HashMap") ||
               fqn.startsWith("java.util.LinkedHashMap") || fqn.startsWith("java.util.TreeMap");
    }

    private static String firstEnumConstant(PsiClass cls) {
        for (PsiField field : cls.getFields()) {
            if (field instanceof PsiEnumConstant) return field.getName();
        }
        return "VALUE";
    }
}
