/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.api.actuate;

import java.beans.PropertyDescriptor;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBean;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

/**
 * Every greenfinger setting that is actually in force, as one answer.
 *
 * <p>
 * Configuration arrives from four places -- the packaged yaml, the copy beside the launcher,
 * {@code .env} and the command line -- and which one won is the first question of most support
 * conversations. Reading the yaml answers it wrongly whenever something overrode it, so this reads
 * the merged objects the code will actually use. Spring's own {@code configprops} would answer the
 * same question about every library in the application; this is greenfinger's own, which is what
 * the System page shows.
 *
 * @Description: SettingsEndpoint
 * @Author: Fred Feng
 * @Date: 26/09/2026
 * @Version 2.0.0
 */
@Endpoint(id = "settings")
@RequiredArgsConstructor
public class SettingsEndpoint {

    private static final String PACKAGE_PREFIX = "com.github.greenfinger";

    static final String MASK = "******";

    private final ApplicationContext applicationContext;

    @ReadOperation
    public Settings settings() {
        List<Group> groups = ConfigurationPropertiesBean.getAll(applicationContext).values().stream()
                .filter(bean -> bean.getInstance().getClass().getPackageName()
                        .startsWith(PACKAGE_PREFIX))
                .map(SettingsEndpoint::groupOf).sorted(Comparator.comparing(Group::prefix))
                .toList();
        return new Settings(groups);
    }

    private static Group groupOf(ConfigurationPropertiesBean bean) {
        Object instance = bean.getInstance();
        Map<String, Object> flat = new TreeMap<>();
        flatten(null, instance, flat);
        return new Group(prefixOf(bean), instance.getClass().getSimpleName(), flat);
    }

    private static String prefixOf(ConfigurationPropertiesBean bean) {
        ConfigurationProperties annotation = bean.getAnnotation();
        return StringUtils.hasText(annotation.prefix()) ? annotation.prefix() : annotation.value();
    }

    /**
     * One row per leaf, keyed the way the yaml keys it -- {@code minio.endpoint} rather than a tree
     * the page would have to walk. Recursion stops at anything that is not ours: a {@code Duration}
     * or a {@code File} is a value, not a group of settings.
     */
    private static void flatten(String path, Object value, Map<String, Object> into) {
        if (value == null) {
            into.put(path, null);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, entry) -> flatten(join(path, String.valueOf(key)), entry, into));
            return;
        }
        if (value instanceof Collection<?> collection) {
            into.put(path, collection.stream().map(SettingsEndpoint::scalar).toList());
            return;
        }
        if (!isOurs(value)) {
            into.put(path, mask(path, scalar(value)));
            return;
        }
        for (PropertyDescriptor property : BeanUtils.getPropertyDescriptors(value.getClass())) {
            if (property.getReadMethod() == null || "class".equals(property.getName())) {
                continue;
            }
            Object read =
                    ReflectionUtils.invokeMethod(property.getReadMethod(), value);
            flatten(join(path, property.getName()), read, into);
        }
    }

    private static boolean isOurs(Object value) {
        Package declared = value.getClass().getPackage();
        return declared != null && declared.getName().startsWith(PACKAGE_PREFIX)
                && !value.getClass().isEnum();
    }

    private static Object scalar(Object value) {
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence) {
            return value;
        }
        return String.valueOf(value);
    }

    private static String join(String path, String name) {
        return path == null ? name : path + "." + name;
    }

    /**
     * Whether a value is worth hiding, decided on the name's ending rather than on the words it
     * contains. {@code maxTextTokens} and {@code tokenValidity} are settings somebody needs to
     * read; {@code tokenSecret} and {@code apiKey} are not.
     */
    static Object mask(String path, Object value) {
        if (!(value instanceof CharSequence text) || text.isEmpty()) {
            return value;
        }
        String name = path == null ? "" : path.substring(path.lastIndexOf('.') + 1).toLowerCase();
        boolean secret = name.endsWith("password") || name.endsWith("secret")
                || name.endsWith("key") || name.endsWith("credential")
                || name.endsWith("credentials") || name.equals("token");
        return secret ? MASK : value;
    }


    /**
     * @param groups one per {@code @ConfigurationProperties} class of ours, by prefix
     */
    public record Settings(List<Group> groups) {
    }

    /**
     * @param prefix     what the yaml calls this group
     * @param type       the class behind it, for whoever goes looking
     * @param properties leaf name to value, secrets already hidden
     */
    public record Group(String prefix, String type, Map<String, Object> properties) {
    }

}
