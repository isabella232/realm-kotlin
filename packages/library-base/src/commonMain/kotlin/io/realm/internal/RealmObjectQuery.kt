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
 *
 */

package io.realm.internal

import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCoreIndexOutOfBoundsException
import io.realm.internal.interop.RealmCoreInvalidQueryException
import io.realm.internal.interop.RealmCoreInvalidQueryStringException
import io.realm.internal.interop.RealmInterop
import io.realm.query.RealmQuery
import io.realm.query.RealmScalarQuery
import io.realm.query.RealmSingleQuery
import io.realm.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@Suppress("SpreadOperator")
internal class RealmObjectQuery<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val clazz: KClass<E>,
    private val mediator: Mediator,
    composedQueryPointer: NativePointer? = null,
    private val filter: String,
    private vararg val args: Any?
) : RealmQuery<E>, Thawable<RealmResultsImpl<E>> {

    private val queryPointer: NativePointer = when {
        composedQueryPointer != null -> composedQueryPointer
        else -> parseQuery()
    }

    private val resultsPointer: NativePointer by lazy {
        RealmInterop.realm_query_find_all(queryPointer)
    }

    constructor(
        composedQueryPointer: NativePointer?,
        objectQuery: RealmObjectQuery<E>
    ) : this(
        objectQuery.realmReference,
        objectQuery.clazz,
        objectQuery.mediator,
        composedQueryPointer,
        objectQuery.filter,
        *objectQuery.args
    )

    override fun find(): RealmResults<E> =
        RealmResultsImpl(realmReference, resultsPointer, clazz, mediator)

    override fun query(filter: String, vararg arguments: Any?): RealmQuery<E> {
        val appendedQuery = tryCatchCoreException {
            RealmInterop.realm_query_append_query(queryPointer, filter, *arguments)
        }
        return RealmObjectQuery(appendedQuery, this)
    }

    // TODO Descriptors are added using 'append_query', which requires an actual predicate. This
    //  might result into query strings like "TRUEPREDICATE AND TRUEPREDICATE SORT(...)". We should
    //  look into how to avoid this, perhaps by exposing a different function that internally
    //  ignores unnecessary default predicates.
    override fun sort(property: String, sortOrder: Sort): RealmQuery<E> =
        query("TRUEPREDICATE SORT($property ${sortOrder.name})")

    override fun sort(
        propertyAndSortOrder: Pair<String, Sort>,
        vararg additionalPropertiesAndOrders: Pair<String, Sort>
    ): RealmQuery<E> {
        val stringBuilder =
            StringBuilder().append("TRUEPREDICATE SORT(${propertyAndSortOrder.first} ${propertyAndSortOrder.second}")
        additionalPropertiesAndOrders.forEach { extraPropertyAndOrder ->
            stringBuilder.append(", ${extraPropertyAndOrder.first} ${extraPropertyAndOrder.second}")
        }
        stringBuilder.append(")")
        return query(stringBuilder.toString())
    }

    override fun distinct(property: String, vararg extraProperties: String): RealmQuery<E> {
        val stringBuilder = StringBuilder().append("TRUEPREDICATE DISTINCT($property")
        extraProperties.forEach { extraProperty ->
            stringBuilder.append(", $extraProperty")
        }
        stringBuilder.append(")")
        return query(stringBuilder.toString())
    }

    override fun limit(limit: Int): RealmQuery<E> = query("TRUEPREDICATE LIMIT($limit)")

    override fun first(): RealmSingleQuery<E> = TODO()

    override fun <T : Any> min(property: String, type: KClass<T>): RealmScalarQuery<T> = TODO()

    override fun <T : Any> max(property: String, type: KClass<T>): RealmScalarQuery<T> = TODO()

    override fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T> = TODO()

    override fun count(): RealmScalarQuery<Long> = TODO()

    override fun asFlow(): Flow<RealmResults<E>> = TODO()

    override fun thaw(liveRealm: RealmReference): RealmResultsImpl<E> = TODO()

    private fun parseQuery(): NativePointer = tryCatchCoreException {
        RealmInterop.realm_query_parse(
            realmReference.dbPointer,
            clazz.simpleName!!,
            filter,
            *args
        )
    }

    private fun tryCatchCoreException(block: () -> NativePointer): NativePointer = try {
        block.invoke()
    } catch (exception: RealmCoreException) {
        throw when (exception) {
            is RealmCoreInvalidQueryStringException ->
                IllegalArgumentException("Wrong query string: ${exception.message}")
            is RealmCoreInvalidQueryException ->
                IllegalArgumentException("Wrong query field provided or malformed syntax in query: ${exception.message}")
            is RealmCoreIndexOutOfBoundsException ->
                IllegalArgumentException("Have you specified all parameters in your query?: ${exception.message}")
            else ->
                genericRealmCoreExceptionHandler(
                    "Invalid syntax in query: ${exception.message}",
                    exception
                )
        }
    }
}
