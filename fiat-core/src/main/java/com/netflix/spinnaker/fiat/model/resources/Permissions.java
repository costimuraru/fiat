/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.model.resources;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.netflix.spinnaker.fiat.model.Authorization;
import java.util.*;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.val;

/**
 * Representation of authorization configuration for a resource. This object is immutable, which
 * makes it challenging when working with Jackson's {{ObjectMapper}} and Spring's
 * {{\@ConfigurationProperties}}. The {@link Builder} is a helper class for the latter use case.
 */
@ToString
@EqualsAndHashCode
public class Permissions {

  public static Permissions EMPTY = new Permissions.Builder().build();

  private final Map<Authorization, List<String>> permissions;

  private Permissions(Map<Authorization, List<String>> p) {
    this.permissions = p;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    for (Map.Entry<Authorization, List<String>> entry : permissions.entrySet()) {
      result.append(entry.getKey() + ":" + entry.getValue().toString());
      result.append(",");
    }
    return result.toString();
  }

  /**
   * Specifically here for Jackson deserialization. Sends data through the {@link Builder} in order
   * to sanitize the input data (just in case).
   */
  @JsonCreator
  public static Permissions factory(Map<Authorization, List<String>> data) {
    return new Builder().set(data).build();
  }

  /** Here specifically for Jackson serialization. */
  @JsonValue
  private Map<Authorization, List<String>> getPermissions() {
    return permissions;
  }

  public Set<String> allGroups() {
    return permissions.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
  }

  public boolean isRestricted() {
    return this.permissions.values().stream().anyMatch(groups -> !groups.isEmpty());
  }

  public boolean isAuthorized(Set<Role> userRoles) {
    return !getAuthorizations(userRoles).isEmpty();
  }

  public boolean isEmpty() {
    return permissions.isEmpty();
  }

  public Set<Authorization> getAuthorizations(Set<Role> userRoles) {
    val r = userRoles.stream().map(Role::getName).collect(Collectors.toList());
    return getAuthorizations(r);
  }

  public Set<Authorization> getAuthorizations(List<String> userRoles) {
    if (!isRestricted()) {
      return Authorization.ALL;
    }

    return this.permissions.entrySet().stream()
        .filter(entry -> !Collections.disjoint(entry.getValue(), userRoles))
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public List<String> get(Authorization a) {
    return permissions.get(a);
  }

  /**
   * This is a helper class for setting up an immutable Permissions object. It also acts as the
   * target Java Object for Spring's ConfigurationProperties deserialization.
   *
   * <p>Objects should be defined on the account config like:
   *
   * <p>someRoot: name: resourceName permissions: read: - role1 - role2 write: - role1
   *
   * <p>Group/Role names are trimmed of whitespace and lowercased.
   */
  public static class Builder extends LinkedHashMap<Authorization, List<String>> {

    @JsonCreator
    public static Builder factory(Map<Authorization, List<String>> data) {
      return new Builder().set(data);
    }

    public Builder set(Map<Authorization, List<String>> p) {
      this.clear();
      this.putAll(p);
      return this;
    }

    public Builder add(Authorization a, String group) {
      this.computeIfAbsent(a, ignored -> new ArrayList<>()).add(group);
      return this;
    }

    public Builder add(Authorization a, List<String> groups) {
      groups.forEach(group -> add(a, group));
      return this;
    }

    public Permissions build() {
      final Map<Authorization, List<String>> perms = new HashMap<>();
      this.forEach(
          (auth, groups) -> {
            List<String> lowerGroups =
                Collections.unmodifiableList(
                    groups.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toList()));
            perms.put(auth, lowerGroups);
          });
      return new Permissions(Collections.unmodifiableMap(perms));
    }
  }
}
