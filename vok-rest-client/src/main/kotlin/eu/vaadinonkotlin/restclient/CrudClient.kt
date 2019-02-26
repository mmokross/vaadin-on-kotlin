package eu.vaadinonkotlin.restclient

import com.github.mvysny.vokdataloader.*
import okhttp3.*

/**
 * Uses the CRUD endpoint and serves instances of given item of type [itemClass] over given [client] using [OkHttpClientVokPlugin.gson].
 * Expect the CRUD endpoint to be exposed in the following manner:
 * * `GET /rest/users` returns all users
 * * `GET /rest/users?select=count` returns a single number - the count of all users. This is only necessary for [getCount]
 * or if you plan to use this client as a backend for Vaadin Grid.
 * * `GET /rest/users/22` returns one users
 * * `POST /rest/users` will create an user
 * * `PATCH /rest/users/22` will update an user
 * * `DELETE /rest/users/22` will delete an user
 *
 * Paging/sorting/filtering is supported: the following query parameters will simply be added to the "get all" URL request:
 *
 * * `limit` and `offset` for result paging. Both must be 0 or greater. The server may impose max value limit on the `limit` parameter.
 * * `sort_by=-last_modified,+email,first_name` - a list of sorting clauses. The server may restrict sorting by only a selected subset of properties.
 * * The filters are simply converted to query parameters, for example `age=81`. [OpFilter]s are also supported: the value will be prefixed with a special operator prefix:
 * `eq:`, `lt:`, `lte:`, `gt:`, `gte:`, `ilike:`, `like:`, `isnull:`, `isnotnull:`, for example `age=lt:25`. A full example is `name=ilike:martin&age=lte:70&age=gte:20&birthdate=isnull:&grade=5`.
 * OR filters are not supported - passing [OrFilter] will cause [getAll] to throw [IllegalArgumentException].
 *
 * All column names are expected to be Kotlin [kotlin.reflect.KProperty1.name] of the entity in question.
 *
 * Since this client is also a [DataLoader], you can use the `DataLoaderAdapter` class from the `vok-util-vaadin8`/`vok-util-vaadin10`
 * module to turn this client into a Vaadin `DataProvider` which you can then feed into Vaadin Grid or ComboBox etc:
 * ```
 * val crud = CrudClient("http://localhost:8080/rest/person/", Person::class.java)
 * val dp = DataLoaderAdapter(Person::class.java, crud, { it.id!! }).withConfigurableFilter2()
 * grid.dataProvider = dp
 * ```
 * @param baseUrl the base URL, such as `http://localhost:8080/rest/users/`, must end with a slash.
 * @param converter used to convert filter values to strings passable as query parameters. Defaults to [QueryParameterConverter] with system-default
 * zone; it is pretty much recommended to set a specific time zone.
 */
class CrudClient<T: Any>(val baseUrl: String, val itemClass: Class<T>,
                         val client: OkHttpClient = OkHttpClientVokPlugin.okHttpClient!!,
                         val converter: Converter<in Any, String> = QueryParameterConverter()) : DataLoader<T> {
    init {
        require(baseUrl.endsWith("/")) { "$baseUrl must end with /" }
    }

    /**
     * Fetches data from the back end. The items must match given [filter]. This function does exactly the same as [fetch].
     * @param filter optional filter which defines filtering to be used for counting the
     * number of items. If null all items are considered.
     * @param sortBy optionally sort the beans according to given fields. By default sorts ASC; if you prepend the field with the "-"
     * character the sorting will be DESC.
     * @param range offset and limit to fetch
     * @return a list of items matching the query, may be empty.
     */
    fun getAll(filter: Filter<in T>? = null, sortBy: List<SortClause> = listOf(), range: LongRange = 0..Long.MAX_VALUE): List<T> {
        val url = buildUrl(baseUrl) {
            if (range != 0..Long.MAX_VALUE) {
                addQueryParameter("offset", range.first.toString())
                addQueryParameter("limit", range.length.toString())
            }
            if (!sortBy.isEmpty()) {
                addQueryParameter("sort_by", sortBy.joinToString(",") { "${if(it.asc)"+" else "-"}${it.propertyName}" })
            }
            if (filter != null) {
                addFilterQueryParameters(filter)
            }
        }
        val request = Request.Builder().url(url).build()
        return client.exec(request) { response -> response.jsonArray(itemClass) }
    }

    private fun HttpUrl.Builder.addFilterQueryParameters(filter: Filter<in T>) {

        fun opToRest(op: CompareOperator): String = when (op) {
            CompareOperator.eq -> "eq"
            CompareOperator.ge -> "gte"
            CompareOperator.gt -> "gt"
            CompareOperator.le -> "lte"
            CompareOperator.lt -> "lt"
        }

        if (filter is BeanFilter) {
            val propName = filter.propertyName
            require(propName != "limit" && propName != "offset" && propName != "sort_by" && propName != "select") {
                "cannot filter on reserved query parameter name $propName"
            }
            val value = if (filter.value == null) null else converter.convert(filter.value!!)
            val restValue = when (filter) {
                is EqFilter -> value
                is IsNotNullFilter -> "isnotnull:"
                is IsNullFilter -> "isnull:"
                is LikeFilter -> "like:$value"
                is ILikeFilter ->  "ilike:$value"
                is OpFilter -> "${opToRest(filter.operator)}:$value"
                else -> throw IllegalArgumentException("Unsupported filter $filter")
            }
            addQueryParameter(propName, restValue)
        } else {
            when (filter) {
                is AndFilter -> filter.children.forEach { addFilterQueryParameters(it) }
                else -> throw IllegalArgumentException("Unsupported filter $filter")
            }
        }
    }

    fun getOne(id: String): T {
        val request = Request.Builder().url("$baseUrl$id").build()
        return client.exec(request) { response -> response.json(itemClass) }
    }

    fun create(entity: T) {
        val body = RequestBody.create(mediaTypeJson, OkHttpClientVokPlugin.gson.toJson(entity))
        val request = Request.Builder().post(body).url(baseUrl).build()
        client.exec(request) {}
    }

    fun update(id: String, entity: T) {
        val body = RequestBody.create(mediaTypeJson, OkHttpClientVokPlugin.gson.toJson(entity))
        val request = Request.Builder().patch(body).url("$baseUrl$id").build()
        client.exec(request) {}
    }

    fun delete(id: String) {
        val request = Request.Builder().delete().url("$baseUrl$id").build()
        client.exec(request) {}
    }

    private fun buildUrl(baseUrl: String, block: HttpUrl.Builder.()->Unit): HttpUrl {
        val url = requireNotNull(HttpUrl.parse(baseUrl)) { "Unparsable url: $baseUrl" }
        return url.newBuilder().apply { block() } .build()
    }

    companion object {
        val mediaTypeJson = MediaType.parse("application/json; charset=utf-8")
    }

    override fun fetch(filter: Filter<T>?, sortBy: List<SortClause>, range: LongRange): List<T> = getAll(filter, sortBy, range.first..range.endInclusive)

    override fun getCount(filter: Filter<T>?): Long {
        val url = buildUrl("$baseUrl?select=count") {
            if (filter != null) {
                addFilterQueryParameters(filter)
            }
        }
        val request = Request.Builder().url(url).build()
        return client.exec(request) { response -> response.string().toLong() }
    }
}
