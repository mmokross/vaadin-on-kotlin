package eu.vaadinonkotlin.restclient

import com.github.mvysny.dynatest.*
import io.javalin.Javalin
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.EmptyResource
import org.eclipse.jetty.webapp.WebAppContext
import java.io.FileNotFoundException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.test.expect

data class Person(var name: String? = null,
                  var surname: String? = null,
                  var age: Int? = null)

class MyJavalinServlet : HttpServlet() {
    private val javalin = Javalin.createStandalone()
        .get("foo") { ctx -> ctx.result(content) }
        .get("fail") { throw RuntimeException() }
        .javalinServlet()

    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
        javalin.service(req, resp)
    }
    companion object {
        var content: String = ""
    }
}

@DynaTestDsl
fun DynaNodeGroup.usingJavalin() {
    lateinit var server: Server
    beforeGroup {
        val ctx = WebAppContext()
        ctx.baseResource = EmptyResource.INSTANCE
        ctx.addServlet(MyJavalinServlet::class.java, "/*")
        server = Server(9876)
        server.handler = ctx
        server.start()
    }
    afterGroup { server.stop() }
}

class OkHttpClientUtilsTest : DynaTest({
    fun String.get(): Request = Request.Builder().url(this).get().build()
    lateinit var request: Request
    beforeEach {
        OkHttpClientVokPlugin().init()
        request = "http://localhost:9876/foo".get()
    }
    fun client(): OkHttpClient = OkHttpClientVokPlugin.okHttpClient!!
    afterEach { OkHttpClientVokPlugin().destroy() }

    usingJavalin()

    test("json") {
        MyJavalinServlet.content = """{"name":"John", "surname":"Doe"}"""
        expect(Person("John", "Doe")) { client().exec(request) { it.json(Person::class.java) } }
    }

    test("404") {
        expectThrows(FileNotFoundException::class, "404: Not found (GET http://localhost:9876/bar)") {
            client().exec("http://localhost:9876/bar".get()) {}
        }
    }

    test("500") {
        expectThrows(HttpResponseException::class, "500: Internal server error") {
            client().exec("http://localhost:9876/fail".get()) {}
        }
    }

    group("jsonArray") {
        test("empty") {
            MyJavalinServlet.content = """[]"""
            expectList() { client().exec(request) { it.jsonArray(Person::class.java) } }
        }
        test("simple") {
            MyJavalinServlet.content = """[{"name":"John", "surname":"Doe"}]"""
            expectList(Person("John", "Doe")) { client().exec(request) { it.jsonArray(Person::class.java) } }
        }
    }

    group("jsonMap") {
        test("empty") {
            MyJavalinServlet.content = """{}"""
            expect(mapOf()) { client().exec(request) { it.jsonMap(Person::class.java) } }
        }
        test("simple") {
            MyJavalinServlet.content = """{"director": {"name":"John", "surname":"Doe"}}"""
            expect(mapOf("director" to Person("John", "Doe"))) { client().exec(request) { it.jsonMap(Person::class.java) } }
        }
    }

    group("buildUrl") {
        test("simple build") {
            expect("http://hello.com/") {
                "http://hello.com".buildUrl {  } .toUrl().toString()
            }
        }
        test("add query parameters") {
            expect("http://hello.com/?q=foo%20bar") {
                "http://hello.com/".buildUrl { addQueryParameter("q", "foo bar") }.toUrl().toString()
            }
        }
        test("fails with invalid URL") {
            expectThrows(IllegalArgumentException::class, "Expected URL scheme 'http' or 'https'") {
                "hello.com".buildUrl {}
            }
        }
    }
})
