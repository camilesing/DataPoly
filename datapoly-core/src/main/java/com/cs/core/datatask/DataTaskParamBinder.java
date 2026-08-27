// Use of this source code is governed by a BSD-style license
package com.cs.core.datatask;

import com.cs.common.dto.BaseParam;
import com.cs.common.dto.ItemParam;
import com.cs.common.exception.CommonException;
import com.cs.common.exception.ResponseErrorCode;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * Binds a submitted parameter payload onto the declaration stored on the task
 * definition. Produces the flat/nested value map expected by XmlSqlTemplate:
 * OBJECT parameters become nested maps (dot-style submitted keys accepted too),
 * arrays keep their shape. Unknown top level keys are ignored deliberately.
 */
public final class DataTaskParamBinder {

    private DataTaskParamBinder() {
    }

    public static Map<String, Object> bind(List<ItemParam> declarations, Map<String, Object> submitted) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (null == declarations || declarations.isEmpty()) {
            return result;
        }
        Map<String, Object> body = null == submitted ? Collections.emptyMap() : submitted;
        for (ItemParam decl : declarations) {
            boolean isArray = Boolean.TRUE.equals(decl.getIsArray());
            Object value;
            if (decl.getType() != null && decl.getType().isObject()) {
                value = bindObjectChildren(decl, body, isArray);
            } else {
                value = bindScalarRoot(decl, body, isArray);
            }
            if (null != value) {
                result.put(decl.getName(), value);
            }
        }
        return result;
    }

    private static Object bindScalarRoot(ItemParam decl, Map<String, Object> body, boolean isArray) {
        Object raw = body.get(decl.getName());
        if (!isArray) {
            return coerceOrNull(decl, raw, decl.getName(), true);
        }
        List<Object> values = raw instanceof List ? (List<Object>) raw : null;
        if (null == values || values.isEmpty()) {
            return requireOrNothing(decl, raw, decl.getName(),
                    () -> defaultList(decl));
        }
        List<Object> out = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            out.add(coerce(decl.getType(), values.get(i),
                    String.format("%s[%d]", decl.getName(), i), true));
        }
        return out;
    }

    /**
     * OBJECT parameters accept either a nested map under the parameter name or flat
     * {@code name.child} keys on the request body; the nested form wins on conflicts.
     */
    private static Object bindObjectChildren(ItemParam decl, Map<String, Object> body, boolean isArray) {
        String root = decl.getName();
        Map<String, Object> container = body.get(root) instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) body.get(root))
                : new LinkedHashMap<>();
        String prefix = root + ".";
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                container.putIfAbsent(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        if (container.isEmpty()) {
            return requireOrNothing(decl, null, root,
                    isArray ? () -> Collections.emptyList()
                            : () -> null /* omitted entirely; optional defaults remain undeclared */);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (isArray) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                    "datatask.param.object.array.unsupported", root);
        }
        List<BaseParam> children = decl.getChildren();
        if (null == children || children.isEmpty()) {
            throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                    "datatask.param.object.children.required", root);
        }
        for (BaseParam child : children) {
            String path = root + "." + child.getName();
            if (Boolean.TRUE.equals(child.getIsArray())) {
                List<Object> raws = container.get(child.getName()) instanceof List
                        ? (List<Object>) container.get(child.getName()) : null;
                if (null == raws || raws.isEmpty()) {
                    if (Boolean.TRUE.equals(child.getRequired())) {
                        throw missing(path);
                    }
                    continue;
                }
                List<Object> items = new ArrayList<>(raws.size());
                for (int i = 0; i < raws.size(); i++) {
                    items.add(coerce(child.getType(), raws.get(i), String.format("%s[%d]", path, i),
                            Boolean.TRUE.equals(child.getRequired())));
                }
                out.put(child.getName(), items);
            } else {
                Object value = coerceOrNull(child, container.get(child.getName()), path,
                        Boolean.TRUE.equals(child.getRequired()));
                if (null != value) {
                    out.put(child.getName(), value);
                }
            }
        }
        return out;
    }

    /** Single value path shared by roots and object children; honors default, enforces required. */
    private static Object coerceOrNull(BaseParam decl, Object raw, String path, boolean required) {
        if (isEmpty(raw)) {
            if (StringUtils.isNotBlank(decl.getDefaultValue())) {
                return coerce(decl.getType(), decl.getDefaultValue(), path, false);
            }
            if (required) {
                throw missing(path);
            }
            return null;
        }
        return coerce(decl.getType(), raw, path, required);
    }

    private interface FallbackSupplier {
        Object get();
    }

    /**
     * Array-of-scalar / OBJECT-missing branches share this: report a missing required
     * parameter, otherwise fall back to the supplied default representation.
     */
    private static Object requireOrNothing(ItemParam decl, Object raw, String path, FallbackSupplier fallback) {
        boolean hasAny = raw instanceof List ? !((List<Object>) raw).isEmpty() : !isEmpty(raw);
        if (!hasAny && Boolean.TRUE.equals(decl.getRequired())) {
            throw missing(path);
        }
        return fallback.get();
    }

    private static Object defaultList(ItemParam decl) {
        return StringUtils.isNotBlank(decl.getDefaultValue())
                ? coerce(decl.getType(), decl.getDefaultValue(), decl.getName(), false)
                : null;
    }

    private static Object coerce(com.cs.common.enums.ParamTypeEnum type, Object raw, String path, boolean enforce) {
        if (null == type) {
            throw new CommonException(ResponseErrorCode.ERROR_INTERNAL_ERROR, "common.parameter.type.null");
        }
        try {
            return type.getConverter().apply(stringify(raw));
        } catch (CommonException ce) {
            throw ce;
        } catch (Exception e) {
            if (enforce) {
                throw new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT,
                        "common.invalid.param.value", path);
            }
            return null;
        }
    }

    private static String stringify(Object raw) {
        return raw instanceof String ? (String) raw : String.valueOf(raw);
    }

    private static boolean isEmpty(Object raw) {
        return null == raw || (raw instanceof String && StringUtils.isBlank((String) raw));
    }

    private static CommonException missing(String path) {
        return new CommonException(ResponseErrorCode.ERROR_INVALID_ARGUMENT, "datatask.param.required", path);
    }
}
