package eu.vaadinonkotlin.vaadin

import com.github.mvysny.karibudsl.v10.DateInterval
import com.github.mvysny.karibudsl.v10.NumberInterval
import com.github.mvysny.kaributools.BrowserTimeZone
import com.github.mvysny.vokdataloader.Filter
import com.vaadin.flow.component.combobox.ComboBox
import eu.vaadinonkotlin.FilterFactory
import eu.vaadinonkotlin.toDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

private fun <T : Comparable<T>, F> T.legeFilter(propertyName: String, filterFactory: FilterFactory<F>, isLe: Boolean): F =
        if (isLe) filterFactory.le(propertyName, this) else filterFactory.ge(propertyName, this)

/**
 * Creates a filter which matches all datetimes within given day.
 *
 * Note: [BrowserTimeZone] is used when comparing [LocalDate] with [Instant], [Date] and [Calendar] instances.
 */
public fun <T: Any> LocalDate.toFilter(propertyName: String,
                       fieldType: Class<*>): Filter<T> =
        DateInterval.of(this).toFilter<Filter<T>>(propertyName, DataLoaderFilterFactory(), fieldType)!!

/**
 * Takes `this` and converts it into a filter `propertyName <= this` or `propertyName >= this`, based
 * on the value of [isLe].
 *
 * Note: [BrowserTimeZone] is used when comparing [LocalDate] with [Instant], [Date] and [Calendar] instances.
 * @param fieldType converts `this` value to a value compatible with this type. Supports [LocalDate],
 * [LocalDateTime], [Instant], [Date] and [Calendar].
 */
private fun <F> LocalDate.toFilter(
        propertyName: String,
        filterFactory: FilterFactory<F>,
        fieldType: Class<*>,
        isLe: Boolean
): F {
    if (fieldType == LocalDate::class.java) {
        return legeFilter(propertyName, filterFactory, isLe)
    }
    val dateTime: LocalDateTime = if (isLe) plusDays(1).atStartOfDay().minusSeconds(1) else atStartOfDay()
    if (fieldType == LocalDateTime::class.java) {
        return dateTime.legeFilter(propertyName, filterFactory, isLe)
    }
    val instant: Instant = dateTime.atZone(BrowserTimeZone.get).toInstant()
    if (fieldType == Instant::class.java) {
        return instant.legeFilter(propertyName, filterFactory, isLe)
    }
    if (Date::class.java.isAssignableFrom(fieldType)) {
        return instant.toDate.legeFilter(propertyName, filterFactory, isLe)
    }
    if (Calendar::class.java.isAssignableFrom(fieldType)) {
        val cal: Calendar = Calendar.getInstance()
        cal.time = instant.toDate
        return cal.legeFilter(propertyName, filterFactory, isLe)
    }
    throw IllegalArgumentException("Parameter fieldType: invalid value ${fieldType}: unsupported date type, can not compare")
}

/**
 * Creates a filter which accepts datetime-like values. Takes this and converts it into a filter `propertyName in this`.
 *
 * Note: [BrowserTimeZone] is used when comparing [LocalDate] with [Instant], [Date] and [Calendar] instances.
 * @param fieldType used to convert [LocalDate] `from`/`to` values of this range to a value
 * comparable with values coming from [propertyName]. Supports [LocalDate],
 * [LocalDateTime], [Instant], [Date] and [Calendar].
 * @throws IllegalArgumentException if [fieldType] is of unsupported type.
 */
public fun <F : Any> DateInterval.toFilter(
        propertyName: String,
        filterFactory: FilterFactory<F>,
        fieldType: Class<*>
): F? {
    val filters: List<F> = listOfNotNull(
            start?.toFilter(propertyName, filterFactory, fieldType, false),
            endInclusive?.toFilter(propertyName, filterFactory, fieldType, true)
    )
    return filterFactory.and(filters.toSet())
}

/**
 * Creates a filter out of this interval, using given [filterFactory].
 * @return a filter which matches the same set of numbers as this interval. Returns `null` for universal set interval.
 */
public fun <N, F> NumberInterval<N>.toFilter(propertyName: String, filterFactory: FilterFactory<F>): F? where N: Number, N: Comparable<N> = when {
    isSingleItem -> filterFactory.eq(propertyName, endInclusive!!)
    isBound -> filterFactory.between(propertyName, start!!, endInclusive!!)
    endInclusive != null -> filterFactory.le(propertyName, endInclusive!!)
    start != null -> filterFactory.ge(propertyName, start!!)
    else -> null
}

/**
 * A very simple [ComboBox] with two pre-filled values: `true` and `false`. Perfect
 * for filters for boolean-based Grid columns since it's 3-state:
 * * `null` (no filter)
 * * `true` (passes only `true` values)
 * * `false` (passes only `false` values)
 */
public open class BooleanComboBox : ComboBox<Boolean>(null, true, false)

/**
 * Creates a very simple [ComboBox] with all enum constants as items. Perfect for
 * filters for enum-based Grid columns.
 * @param E the enum type
 * @param items options in the combo box, defaults to all constants of [E].
 */
public inline fun <reified E: Enum<E>> enumComboBox(
        items: List<E> = E::class.java.enumConstants.toList()
): ComboBox<E?> = ComboBox<E?>().apply {
    // no need to explicitly add the `null` item here since the ComboBox is clearable:
    // the user can click the "X" button to set the combo box value to null.
    setItems(items)
    isClearButtonVisible = true
    setItemLabelGenerator { item: E? -> item?.name ?: "" }
}
