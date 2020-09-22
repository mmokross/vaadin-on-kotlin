package eu.vaadinonkotlin.vaadin10.vokdb

import com.github.mvysny.vokdataloader.DataLoader
import com.github.mvysny.vokdataloader.DataLoaderPropertyName
import com.github.vokorm.KEntity
import com.github.vokorm.dataloader.EntityDataLoader
import com.github.vokorm.dataloader.SqlDataLoader
import com.gitlab.mvysny.jdbiorm.Dao
import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.data.binder.HasDataProvider
import com.vaadin.flow.data.provider.DataProvider
import eu.vaadinonkotlin.vaadin10.DataLoaderAdapter
import eu.vaadinonkotlin.vaadin10.VokDataProvider
import eu.vaadinonkotlin.vaadin10.asDataProvider
import kotlin.reflect.KProperty1
import eu.vaadinonkotlin.vaadin10.withStringFilterOn as withStringFilterOn1

/**
 * Provides instances of entities of given class from a database. Does not support joins on any of the like; supports filtering
 * and sorting. Only supports simple views over one database table (one entity) - for anything more complex please use [sqlDataProvider].
 *
 * Example of use: `grid.dataProvider = Person.dataProvider`.
 */
@Deprecated("Use dataLoader")
public inline val <reified T: KEntity<*>> Dao<T, *>.dataProvider: VokDataProvider<T>
    get() = dataLoader.asDataProvider()

/**
 * Provides instances of this entity from a database. Does not support joins on any of the like; supports filtering
 * and sorting. Only supports simple views over one database table (one entity) - for anything more complex please use [sqlDataProvider].
 *
 * Example of use: `grid.setDataLoader(Person.dataLoader)`.
 */
public inline val <reified T: KEntity<*>> Dao<T, *>.dataLoader: DataLoader<T>
    get() = EntityDataLoader(this)

/**
 * Allows the coder to write any SQL he wishes. This provider must be simple enough to not to get in the way by smart (complex) Kotlin language features.
 * It should support any SQL select, but should allow for adding custom filters and orderings (since this is plugged into Grid after all).
 *
 * The provider is bound to a *holder class* which holds the values (any POJO). vok-orm is used to map the result set to the class. For example:
 *
 * ```
 * data class CustomerAddress(val customerName: String, val address: String)
 *
 * val provider = sqlDataProvider(CustomerAddress::class.java, """select c.name as customerName, a.street || ' ' || a.city as address
 *     from Customer c inner join Address a on c.address_id=a.id where 1=1 {{WHERE}} order by 1=1{{ORDER}} {{PAGING}}""", idMapper = { it })
 * ```
 *
 * (Note how select column names must correspond to field names in the `CustomerAddress` class)
 *
 * Now `sqlDataProvider` can hot-patch the `where` clause computed from Grid's filters into `{{WHERE}}` (as a simple string replacement),
 * and the `order by` and `offset`/`limit` into the `{{ORDER}}` and `{{PAGING}}`, as follows:
 *
 * * `{{WHERE}}` will be replaced by something like this: `"and name=:pqw5as and age>:p123a"` - note the auto-generated parameter
 *   names starting with `p`. If there are no filters, will be replaced by an empty string.
 * * `{{ORDER}}` will be replaced by `", customerName ASC, street ASC"` or by an empty string if there is no ordering requirement.
 * * `{{PAGING}}` will be replaced by `"offset 0 limit 100"` or by an empty string if there are no limitations.
 *
 * Note that the Grid will display fields present in the `CustomerAddress` holder class and will also auto-generate filters
 * for them, based on the type of the field.
 *
 * No bloody annotations! Work in progress. It is expected that a holder class is written for every select, tailored to show the outcome
 * of that particular select.
 *
 * @param clazz the type of the holder class which will hold the result
 * @param sql the select which can map into the holder class (that is, it selects columns whose names match the holder class fields). It should contain
 * `{{WHERE}}`, `{{ORDER}}` and `{{PAGING}}` strings which will be replaced by a simple substring replacement.
 * @param params the [sql] may be parametrized; this map holds all the parameters present in the sql itself.
 * @param idMapper returns the primary key which must be unique for every row returned. If the holder class is a data class and/or has proper equals/hashcode, the class itself may act as the key; in such case
 * just pass in identity here: `{ it }`
 * @param T the type of the holder class.
 * @author mavi
 */
@Deprecated("Use SqlDataLoader class directly")
public fun <T: Any> sqlDataProvider(clazz: Class<T>,
                             sql: String,
                             params: Map<String, Any?> = mapOf(),
                             idMapper: (T)->Any) : VokDataProvider<T>
        = SqlDataLoader(clazz, sql, params).asDataProvider(idMapper)

/**
 * Sets given data loader to this Grid, by the means of wrapping the data loader via [DataLoaderAdapter] and setting it
 * as a (configurable) [Grid.getDataProvider].
 */
public fun <T: KEntity<*>> HasDataProvider<T>.setDataLoader(dataLoader: DataLoader<T>) {
    setDataProvider(dataLoader.asDataProvider())
}

/**
 * Returns a [VokDataProvider] which loads data from this [DataLoader].
 */
public fun <T: KEntity<*>> DataLoader<T>.asDataProvider(): VokDataProvider<T> = DataLoaderAdapter(this) { it.id!! }

/**
 * Creates a data provider which performs string filtering on given [property]. Ideal for [ComboBox] which lazily
 * filters items as the user types in search phrase. Emits [com.github.mvysny.vokdataloader.StartsWithFilter] to the receiver.
 */
public fun <T : KEntity<*>> DataLoader<T>.withStringFilterOn(property: KProperty1<T, String?>): DataProvider<T, String?> =
        withStringFilterOn(property.name)

/**
 * Creates a data provider which performs string filtering on given [property]. Ideal for [ComboBox] which lazily
 * filters items as the user types in search phrase. Emits [com.github.mvysny.vokdataloader.StartsWithFilter] to the receiver.
 */
public fun <T : KEntity<*>> DataLoader<T>.withStringFilterOn(property: DataLoaderPropertyName): DataProvider<T, String?> =
        withStringFilterOn1(property) { it.id!! }
