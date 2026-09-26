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

package com.github.greenfinger.api.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.github.greenfinger.core.WebCrawlerException;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads {@code users.xml} into what Spring Security expects.
 *
 * <p>
 * The accounts are handed out up front -- there is no registration, no user table and no password
 * reset, because greenfinger is an operator's tool rather than a public site. They live in a file
 * of their own rather than in a setting: a list of people does not belong on one line of
 * {@code .env}, and an account is not something a support conversation should ever print.
 *
 * <p>
 * Two roles. {@code ADMIN} crawls and searches; {@code SUPPORT} searches. The chain decides it in
 * one line -- GET passes for either, everything else needs ADMIN -- and this file is only where
 * the names come from.
 *
 * @Description: PreAllocatedUsers
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@Slf4j
public abstract class PreAllocatedUsers {

    static final String DEFAULT_ROLE = "SUPPORT";

    /**
     * The accounts in {@code path}, or the shipped ones when there is no file there.
     *
     * <p>
     * A missing file is not an error: an installation that has not been configured still has to
     * start, or nobody can sign in to configure it. It is a warning, because the shipped passwords
     * are published ones.
     */
    public static List<UserDetails> load(String path) {
        File file = new File(path);
        if (!file.isFile()) {
            log.warn("No {} -- signing in with the shipped admin/tester accounts, whose passwords"
                    + " are published. Put your own in that file.", file.getAbsolutePath());
            return List.of(user("admin", "admin123", "ADMIN"), user("tester", "tester123",
                    DEFAULT_ROLE));
        }
        try (InputStream in = Files.newInputStream(file.toPath())) {
            return parse(in, file.getAbsolutePath());
        } catch (IOException e) {
            throw new WebCrawlerException("Cannot read " + file.getAbsolutePath(), e);
        }
    }

    /**
     * Parses {@code <users><user name password roles/></users>}. Roles are separated by a comma or
     * a bar, and an account with none is a {@code SUPPORT} one -- the safe half of the two.
     *
     * @param where what to name in a message, since a stream cannot say where it came from
     */
    public static List<UserDetails> parse(InputStream in, String where) {
        NodeList entries;
        try {
            entries = documentBuilderFactory().newDocumentBuilder().parse(in)
                    .getElementsByTagName("user");
        } catch (Exception e) {
            throw new WebCrawlerException(where + " is not readable as xml: " + e.getMessage(), e);
        }
        List<UserDetails> users = new ArrayList<>();
        for (int i = 0; i < entries.getLength(); i++) {
            users.add(userOf((Element) entries.item(i), where));
        }
        if (users.isEmpty()) {
            throw new WebCrawlerException("No account in " + where
                    + ". One <user name=\"...\" password=\"...\" roles=\"ADMIN\"/> at least.");
        }
        return users;
    }

    private static UserDetails userOf(Element entry, String where) {
        String name = entry.getAttribute("name").trim();
        String password = entry.getAttribute("password").trim();
        if (StringUtils.isAnyBlank(name, password)) {
            throw new WebCrawlerException(
                    "An account in " + where + " has no name or no password.");
        }
        String roles = entry.getAttribute("roles").trim();
        return user(name, password, StringUtils.isNotBlank(roles) ? roles : DEFAULT_ROLE);
    }

    /**
     * The password is stored as {@code {noop}} because it arrived in plain text: hashing it here
     * would only hide from the reader that whoever can read the file already knows it.
     */
    private static UserDetails user(String name, String password, String roles) {
        return User.withUsername(name).password("{noop}" + password)
                .roles(StringUtils.split(roles, ",|")).build();
    }

    /** No external entities: this file is read at startup, before anybody has signed in. */
    private static DocumentBuilderFactory documentBuilderFactory()
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);
        return factory;
    }

}
