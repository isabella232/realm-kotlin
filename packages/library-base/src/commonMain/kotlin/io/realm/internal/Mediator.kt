/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.internal

import io.realm.RealmObject
import kotlin.reflect.KClass

// TODO Public due to being a transative dependency of ConfigurationImpl
public interface Mediator {
    // TODO OPTIMIZE Most usage of this could be done from cached RealmObjectCompanion instance.
    //  Maybe just eliminate this method to ensure that we don't misuse it in favor of
    //  companionOf(clazz).`$realm$newInstance`()
    public fun createInstanceOf(clazz: KClass<out RealmObject>): RealmObjectInternal
    public fun companionOf(clazz: KClass<out RealmObject>): RealmObjectCompanion
}
