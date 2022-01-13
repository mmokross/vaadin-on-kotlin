package eu.vaadinonkotlin.security

import com.github.mvysny.kaributesting.v10.MockVaadin
import com.github.mvysny.dynatest.DynaTest
import com.github.mvysny.dynatest.expectThrows
import com.github.mvysny.kaributesting.v10.Routes
import com.github.mvysny.kaributesting.v10.expectView
import com.github.mvysny.kaributesting.v10.mock.MockedUI
import com.github.mvysny.kaributools.navigateTo
import com.vaadin.flow.component.UI
import eu.vaadinonkotlin.VaadinOnKotlin
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.router.*
import com.vaadin.flow.server.VaadinRequest
import com.vaadin.flow.server.auth.AccessAnnotationChecker
import com.vaadin.flow.server.auth.ViewAccessChecker
import java.io.Serializable
import java.security.Principal
import javax.servlet.http.HttpServletRequest

/**
 * A view with no parent layout.
 */
@AllowRoles("admin")
@Route("admin")
class AdminView : VerticalLayout()

@Route("login")
class LoginView : VerticalLayout()

@AllowAll
class MyLayout : VerticalLayout(), RouterLayout

/**
 * A view with parent layout.
 */
@Route("user", layout = MyLayout::class)
@AllowRoles("user")
class UserView : VerticalLayout()

@AllowRoles("sales")
class SalesLayout : VerticalLayout(), RouterLayout

/**
 * This view can not be effectively viewed with 'user' since its parent layout lacks the 'user' role.
 */
@AllowRoles("sales", "user")
@Route("sales/sale", layout = SalesLayout::class)
class SalesView : VerticalLayout()

/**
 * This view can not be effectively viewed with anybody.
 */
@AllowRoles()
@Route("rejectall")
class RejectAllView : VerticalLayout()

class VokSecurityTest : DynaTest({
    group("ViewAccessChecker") {
        lateinit var routes: Routes
        beforeGroup {
            VaadinOnKotlin.loggedInUserResolver = DummyUserResolver
            routes = Routes().autoDiscoverViews("eu.vaadinonkotlin.security")
        }
        beforeEach {
            DummyUserResolver.userWithRoles = null
            MockVaadin.setup(routes, uiFactory = { MockedUIWithViewAccessChecker() })
        }
        afterEach {
            MockVaadin.tearDown()
            DummyUserResolver.userWithRoles = null
        }

        test("no user logged in") {
            DummyUserResolver.userWithRoles = null
            navigateTo<AdminView>()
            expectView<LoginView>()

            navigateTo<UserView>()
            expectView<LoginView>()

            navigateTo<SalesView>()
            expectView<LoginView>()

            navigateTo<RejectAllView>()
            expectView<LoginView>()
        }
        test("admin logged in") {
            DummyUserResolver.userWithRoles = setOf("admin")
            navigateTo<AdminView>()
            expectView<AdminView>()

            navigateTo<UserView>()
            expectView<LoginView>()

            navigateTo<SalesView>()
            expectView<LoginView>()

            navigateTo<RejectAllView>()
            expectView<LoginView>() // always allow to display this
        }
        test("user logged in") {
            DummyUserResolver.userWithRoles = setOf("user")

            navigateTo<AdminView>()
            expectView<LoginView>()

            navigateTo<UserView>()
            expectView<UserView>()

            navigateTo<SalesView>()
            expectView<SalesView>()

            navigateTo<RejectAllView>()
            expectView<LoginView>()
        }
        test("sales logged in") {
            DummyUserResolver.userWithRoles = setOf("sales")

            navigateTo<AdminView>()
            expectView<LoginView>()

            navigateTo<UserView>()
            expectView<LoginView>()

            navigateTo<SalesView>()
            expectView<SalesView>()

            navigateTo<RejectAllView>()
            expectView<LoginView>()
        }
    }
})

class MockedUIWithViewAccessChecker : MockedUI() {
    override fun init(request: VaadinRequest) {
        super.init(request)
        val checker = VokViewAccessChecker()
        checker.setLoginView(LoginView::class.java)
        addBeforeEnterListener(checker)
    }
}
