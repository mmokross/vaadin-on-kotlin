package eu.vaadinonkotlin.vaadin8

import com.github.mvysny.karibudsl.v8.*
import eu.vaadinonkotlin.FilterFactory
import eu.vaadinonkotlin.toDate
import com.vaadin.data.Binder
import com.vaadin.shared.ui.datefield.DateTimeResolution
import com.vaadin.ui.*
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A potentially open numeric range. If both [min] and [max] are `null`, then the interval accepts any number.
 * @property min the minimum accepted value, inclusive. If `null` then the numeric range has no lower limit.
 * @property max the maximum accepted value, inclusive. If `null` then the numeric range has no upper limit.
 */
public data class NumberInterval<T : Number>(var min: T?, var max: T?) : Serializable {

    /**
     * Creates a filter out of this interval, using given [filterFactory].
     * @return a filter which matches the same set of numbers as this interval. Returns `null` for universal set interval.
     */
    public fun <F> toFilter(propertyName: String, filterFactory: FilterFactory<F>): F? {
        if (isSingleItem) return filterFactory.eq(propertyName, max!!)
        if (max != null && min != null) {
            return filterFactory.between(propertyName, min!!, max!!)
        }
        if (max != null) return filterFactory.le(propertyName, max!!)
        if (min != null) return filterFactory.ge(propertyName, min!!)
        return null
    }

    /**
     * True if the interval consists of single number only.
     */
    val isSingleItem: Boolean
        get() = max != null && min != null && max == min

    /**
     * True if the interval includes all possible numbers (both [min] and [max] are `null`).
     */
    val isUniversalSet: Boolean
        get() = max == null && min == null
}

/**
 * Only shows a single button as its contents. When the button is clicked, it opens a dialog and allows the user to specify a range
 * of numbers. When the user sets the values, the dialog is
 * hidden and the number range is set as the value of the popup.
 *
 * The current numeric range is also displayed as the caption of the button.
 */
public class NumberFilterPopup : CustomField<NumberInterval<Double>?>() {

    private lateinit var ltInput: TextField
    private lateinit var gtInput: TextField
    @Suppress("UNCHECKED_CAST")
    private val binder: Binder<NumberInterval<Double>> = Binder(NumberInterval::class.java as Class<NumberInterval<Double>>).apply { bean = NumberInterval(null, null) }
    private var internalValue: NumberInterval<Double>? = null

    override fun initContent(): Component = KPopupView().apply {
        w = fillParent; minimizedValueAsHTML = vt["filter.all"]; isHideOnMouseOut = false
        lazy {
            verticalLayout {
                w = wrapContent
                horizontalLayout {
                    gtInput = textField {
                        placeholder = vt["filter.atleast"]
                        w = 100.px
                        bind(binder).toDouble().bind(NumberInterval<Double>::min)
                    }
                    label("") {
                        w = wrapContent
                    }
                    ltInput = textField {
                        placeholder = vt["filter.atmost"]
                        w = 100.px
                        bind(binder).toDouble().bind(NumberInterval<Double>::max)
                    }
                }
                horizontalLayout {
                    alignment = Alignment.MIDDLE_RIGHT
                    button(vt["filter.clear"]) {
                        onLeftClick {
                            binder.fields.forEach { it.clear() }
                            setValue(null, true)
                            isPopupVisible = false
                        }
                    }
                    button(vt["filter.ok"]) {
                        onLeftClick {
                            val copy = binder.bean.copy()
                            setValue(if (copy.isUniversalSet) null else copy, true)
                            isPopupVisible = false
                        }
                    }
                }
            }
            updateReadOnly()
        }
    }

    override fun setReadOnly(readOnly: Boolean) {
        super.setReadOnly(readOnly)
        updateReadOnly()
    }

    private fun updateReadOnly() {
        if (::ltInput.isInitialized) {
            ltInput.isEnabled = !isReadOnly
            gtInput.isEnabled = !isReadOnly
        }
    }

    private fun updateCaption() {
        val content = content as KPopupView
        val value = value
        if (value == null || value.isUniversalSet) {
            content.minimizedValueAsHTML = vt["filter.all"]
        } else {
            if (value.isSingleItem) {
                content.minimizedValueAsHTML = "[x] = ${value.max}"
            } else if (value.min != null && value.max != null) {
                content.minimizedValueAsHTML = "${value.min} ≤ [x] ≤ ${value.max}"
            } else if (value.min != null) {
                content.minimizedValueAsHTML = "[x] ≥ ${value.min}"
            } else if (value.max != null) {
                content.minimizedValueAsHTML = "[x] ≤ ${value.max}"
            }
        }
    }

    override fun doSetValue(value: NumberInterval<Double>?) {
        internalValue = value?.copy()
        binder.bean = value?.copy() ?: NumberInterval<Double>(null, null)
        updateCaption()
    }

    override fun getValue(): NumberInterval<Double>? = internalValue?.copy()

    public var isPopupVisible: Boolean
        get() = (content as KPopupView).isPopupVisible
        set(value) {
            (content as KPopupView).isPopupVisible = value
        }
}

/**
 * Converts this class to its non-primitive counterpart. For example, converts `int.class` to `Integer.class`.
 * @return converts class of primitive type to appropriate non-primitive class; other classes are simply returned as-is.
 */
@Suppress("UNCHECKED_CAST")
public val <T> Class<T>.nonPrimitive: Class<T> get() = when(this) {
    Integer.TYPE -> Integer::class.java as Class<T>
    java.lang.Long.TYPE -> Long::class.java as Class<T>
    java.lang.Float.TYPE -> Float::class.java as Class<T>
    java.lang.Double.TYPE -> java.lang.Double::class.java as Class<T>
    java.lang.Short.TYPE -> Short::class.java as Class<T>
    java.lang.Byte.TYPE -> Byte::class.java as Class<T>
    else -> this
}

public fun <F : Any> DateInterval.toFilter(propertyName: String, filterFactory: FilterFactory<F>, fieldType: Class<*>): F? {
    fun <T : Comparable<T>, F> T.legeFilter(propertyName: String, filterFactory: FilterFactory<F>, isLe: Boolean): F =
            if (isLe) filterFactory.le(propertyName, this) else filterFactory.ge(propertyName, this)

    fun <F> LocalDateTime.toFilter(propertyName: String, filterFactory: FilterFactory<F>, fieldType: Class<*>, isLe: Boolean): F {
        return when (fieldType) {
            LocalDateTime::class.java -> legeFilter(propertyName, filterFactory, isLe)
            LocalDate::class.java -> toLocalDate().legeFilter(propertyName, filterFactory, isLe)
            else -> {
                atZone(browserTimeZone).toInstant().toDate.legeFilter(propertyName, filterFactory, isLe)
            }
        }
    }

    val filters: List<F> = listOf(from?.toFilter(propertyName, filterFactory, fieldType, false), to?.toFilter(propertyName, filterFactory, fieldType, true)).filterNotNull()
    return filterFactory.and(filters.toSet())
}

/**
 * Only shows a single button as its contents. When the button is clicked, it opens a dialog and allows the user to specify a range
 * of dates. When the user sets the values, the dialog is
 * hidden and the date range is set as the value of the popup.
 *
 * The current date range is also displayed as the caption of the button.
 */
public class DateFilterPopup: CustomField<DateInterval?>() {
    private val formatter: DateTimeFormatter get() = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(UI.getCurrent().locale!!)
    private lateinit var fromField: InlineDateTimeField
    private lateinit var toField: InlineDateTimeField
    private lateinit var set: Button
    private lateinit var clear: Button
    /**
     * The desired resolution of this filter popup, defaults to [DateTimeResolution.MINUTE].
     */
    public var resolution: DateTimeResolution = DateTimeResolution.MINUTE
        set(value: DateTimeResolution) {
            field = value
            updateFields()
        }

    private var internalValue: DateInterval? = null

    init {
        styleName = "datefilterpopup"
        // force initcontents so that fromField and toField are initialized and one can set resolution to them
        content
    }

    override fun doSetValue(value: DateInterval?) {
        internalValue = value?.copy()
        updateValueToFields()
        updateCaption()
    }

    private fun updateValueToFields() {
        if (isPopupInitialized) {
            fromField.value = internalValue?.from
            toField.value = internalValue?.to
        }
    }

    override fun getValue(): DateInterval? = internalValue?.copy()

    private fun format(date: LocalDateTime?) = if (date == null) "" else formatter.format(date)

    private fun updateCaption() {
        val content = content as KPopupView
        val value = value
        if (value == null || value.isUniversalSet) {
            content.minimizedValueAsHTML = vt["filter.all"]
        } else {
            content.minimizedValueAsHTML = "${format(value.from)} - ${format(value.to)}"
        }
    }

    private fun truncateDate(date: LocalDateTime?, resolution: DateTimeResolution, start: Boolean): LocalDateTime? {
        @Suppress("NAME_SHADOWING")
        var date = date ?: return null
        for (res in DateTimeResolution.values().slice(0..resolution.ordinal - 1)) {
            if (res == DateTimeResolution.SECOND) {
                date = date.withSecond(if (start) 0 else 59)
            } else if (res == DateTimeResolution.MINUTE) {
                date = date.withMinute(if (start) 0 else 59)
            } else if (res == DateTimeResolution.HOUR) {
                date = date.withHour(if (start) 0 else 23)
            } else if (res == DateTimeResolution.DAY) {
                date = date.withDayOfMonth(if (start) 1 else date.toLocalDate().lengthOfMonth())
            } else if (res == DateTimeResolution.MONTH) {
                date = date.withMonth(if (start) 1 else 12)
            }
        }
        date = date.withNano(if (start) 0 else 999)
        return date
    }

    override fun initContent(): Component = KPopupView().apply {
        w = fillParent; minimizedValueAsHTML = vt["filter.all"]; isHideOnMouseOut = false
        lazy {
            verticalLayout {
                styleName = "datefilterpopupcontent"; setSizeUndefined(); isSpacing = true; isMargin = true

                horizontalLayout {
                    isSpacing = true
                    fromField = inlineDateTimeField {
                        locale = this@DateFilterPopup.locale
                    }
                    toField = inlineDateTimeField {
                        locale = this@DateFilterPopup.locale
                    }
                }
                horizontalLayout {
                    alignment = Alignment.BOTTOM_RIGHT
                    isSpacing = true
                    set = button(vt["filter.set"]) {
                        onLeftClick {
                            value = DateInterval(
                                    truncateDate(fromField.value, resolution, true),
                                    truncateDate(toField.value, resolution, false)
                            )
                            isPopupVisible = false
                        }
                    }
                    clear = button(vt["filter.clear"]) {
                        onLeftClick {
                            value = null
                            isPopupVisible = false
                        }
                    }
                }
            }

            updateValueToFields()
            updateFields()
        }
    }

    override fun setReadOnly(readOnly: Boolean) {
        super.setReadOnly(readOnly)
        updateFields()
    }

    private val isPopupInitialized: Boolean get() = ::set.isInitialized

    private fun updateFields() {
        if (isPopupInitialized) {
            set.isEnabled = !isReadOnly
            clear.isEnabled = !isReadOnly
            fromField.isEnabled = !isReadOnly
            toField.isEnabled = !isReadOnly
            fromField.resolution = resolution
            toField.resolution = resolution
        }
    }

    public var isPopupVisible: Boolean
        get() = (content as KPopupView).isPopupVisible
        set(value) {
            (content as KPopupView).isPopupVisible = value
        }
}

@VaadinDsl
public fun (@VaadinDsl HasComponents).dateRangePopup(value: DateInterval? = null, block: (@VaadinDsl DateFilterPopup).()->Unit = {}): DateFilterPopup {
    val popup = DateFilterPopup()
    if (value != null) {
        popup.value = value
    }
    return init(popup, block)
}
