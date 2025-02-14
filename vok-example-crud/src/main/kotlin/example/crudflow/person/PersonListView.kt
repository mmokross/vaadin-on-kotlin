package example.crudflow.person

import com.github.mvysny.karibudsl.v10.*
import com.github.mvysny.kaributools.*
import com.github.mvysny.kaributools.ModifierKey.*
import com.github.mvysny.vokdataloader.asc
import com.github.vokorm.dataloader.dataLoader
import com.github.vokorm.db
import com.vaadin.flow.component.Key.KEY_G
import com.vaadin.flow.component.button.Button
import com.vaadin.flow.component.button.ButtonVariant
import com.vaadin.flow.component.grid.ColumnTextAlign
import com.vaadin.flow.component.grid.Grid
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu
import com.vaadin.flow.component.icon.IconFactory
import com.vaadin.flow.component.icon.VaadinIcon
import com.vaadin.flow.component.textfield.TextField
import com.vaadin.flow.data.renderer.ComponentRenderer
import com.vaadin.flow.router.Route
import eu.vaadinonkotlin.toDate
import eu.vaadinonkotlin.vaadin.*
import eu.vaadinonkotlin.vaadin.vokdb.setDataLoader
import eu.vaadinonkotlin.vaadin.vokdb.sort
import example.crudflow.MainLayout
import java.time.LocalDate

/**
 * The main view contains a button and a template element.
 */
@Route("", layout = MainLayout::class)
class PersonListView : KComposite() {
    private lateinit var personGrid: Grid<Person>
    lateinit var gridContextMenu: GridContextMenu<Person>

    private val root = ui {
        verticalLayout {
            setSizeFull()
            h4("Person list")
            button("Generate testing data (Alt+G)") {
                onLeftClick {
                    generateTestingData()
                }
                addClickShortcut(Alt + KEY_G)
            }
            personGrid = grid<Person> {
                flexGrow = 1.0
                appendHeaderRow() // because of https://github.com/vaadin/vaadin-grid/issues/1870
                setDataLoader(Person.dataLoader)
                val filterBar: VokFilterBar<Person> = appendHeaderRow().asFilterBar(this)

                addButtonColumn(VaadinIcon.EYE, "view", { person: Person -> navigateTo(PersonView::class, person.id!!) }) {}
                addButtonColumn(VaadinIcon.EDIT, "edit", { person: Person -> createOrEditPerson(person) }) {}
                addButtonColumn(VaadinIcon.TRASH, "delete", { person: Person -> person.delete(); refresh() }) {}

                columnFor(Person::id, sortable = false) {
                    width = "90px"; isExpand = false
                }
                columnFor(Person::name) {
                    filterBar.forField(TextField(), this).istartsWith()
                }
                columnFor(Person::age) {
                    width = "120px"; isExpand = false; textAlign = ColumnTextAlign.CENTER
                    filterBar.forField(NumberRangePopup(), this).inRange()
                }
                columnFor(Person::alive) {
                    width = "130px"; isExpand = false
                    filterBar.forField(BooleanComboBox(), this).eq()
                }
                columnFor(Person::dateOfBirth, converter = { it?.toString() }) {
                    filterBar.forField(DateRangePopup(), this).inRange(Person::dateOfBirth)
                }
                columnFor(Person::maritalStatus) {
                    width = "160px"; isExpand = false
                    filterBar.forField(enumComboBox<MaritalStatus>(), this).eq()
                }
                columnFor(Person::created, converter = { it!!.toInstant().toString() }) {
                    filterBar.forField(DateRangePopup(), this).inRange(Person::created)
                }

                gridContextMenu = gridContextMenu {
                    item("view", { person: Person? -> if (person != null) navigateTo(PersonView::class, person.id!!) })
                    item("edit", { person: Person? -> if (person != null) createOrEditPerson(person) })
                    item("delete", { person: Person? -> if (person != null) { person.delete(); refresh() } })
                }

                sort(Person::name.asc)
            }
        }
    }

    private fun createOrEditPerson(person: Person) {
        CreateEditPerson(person).apply {
            onSaveOrCreateListener = { personGrid.refresh() }
        }.open()
    }

    private fun generateTestingData() {
        db {
            (0..85).forEach {
                Person(name = "generated$it", age = it + 15, maritalStatus = MaritalStatus.Single, alive = true,
                        dateOfBirth = LocalDate.of(1990, 1, 1).plusDays(it.toLong()),
                        created = LocalDate.of(2011, 1, 1).plusDays(it.toLong()).atStartOfDay(BrowserTimeZone.get).toInstant().toDate).save()
            }
        }
        personGrid.dataProvider.refreshAll()
    }
}

/**
 * Utility method which adds a column housing one small icon button with given [icon] and [clickListener].
 */
fun <T> Grid<T>.addButtonColumn(icon: IconFactory, key: String, clickListener: (T) -> Unit, block: Grid.Column<T>.()->Unit): Grid.Column<T> {
    val renderer = ComponentRenderer<Button, T> { row: T ->
        val button = Button(icon.create())
        button.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL)
        button.addClickListener { clickListener(row) }
        button
    }
    val column: Grid.Column<T> = addColumn(renderer).setKey(key).setWidth("50px")
    column.isExpand = false
    column.block()
    return column
}