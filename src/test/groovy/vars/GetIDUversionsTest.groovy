package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

import java.time.LocalDate

class GetIDUversionsTest extends BasePipelineTest {

    def script
    def lastHttpRequestUrls = []

    @Before
    void setUp() {
        super.setUp()
        lastHttpRequestUrls = []
        helper.registerAllowedMethod('reportUsage', [String], { String path -> })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestUrls << args.url
            return [content: '{"items":[],"continuationToken":null}']
        })
        script = loadScript('vars/getIDUversions.groovy')
    }

    @Test
    void aggregatesAcrossContinuationAndAppliesFilters() {
        def today = LocalDate.now()
        def recentPath1 = "/idu/sdk-s8000/ING/${today.minusDays(10)}/S8000-1.2.3-b45.sit"
        def recentPath2 = "/idu/sdk-s8000/ING/${today.minusDays(1)}/S8000-2.0.0-b1.sit"
        def oldPath = "/idu/sdk-s8000/ING/${today.minusDays(100)}/S8000-old.sit"

        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestUrls << args.url
            if (args.url.contains('continuationToken=')) {
                return [content: """
                {
                  "items": [
                    {"path": "${recentPath2}", "lastModified": "${today.minusDays(1)}T00:00:00Z"}
                  ],
                  "continuationToken": null
                }
                """]
            }
            return [content: """
            {
              "items": [
                {"path": "${recentPath1}", "lastModified": "${today.minusDays(10)}T00:00:00Z"},
                {"path": "${oldPath}", "lastModified": "${today.minusDays(100)}T00:00:00Z"},
                {"path": "/other/prefix/${today}/skip.sit", "lastModified": "${today}T00:00:00Z"}
              ],
              "continuationToken": "token-1"
            }
            """]
        })

        def result = script.call([
            baseUrl    : 'http://nexus.example/api',
            repository : 'repoX',
            namePattern: 'patternX',
            pathPrefix : '/idu/sdk-s8000/ING/',
            daysBack   : 40
        ])

        assert lastHttpRequestUrls[0] == 'http://nexus.example/api?repository=repoX&name=patternX'
        assert lastHttpRequestUrls[1] == 'http://nexus.example/api?repository=repoX&name=patternX&continuationToken=token-1'
        assert result == [
            'S8000-1.2.3-b45': recentPath1,
            'S8000-2.0.0-b1' : recentPath2
        ]
    }

    @Test
    void respectsDaysBackFilter() {
        def today = LocalDate.now()
        def freshPath = "/idu/sdk-s8000/ING/${today}/fresh.sit"
        def stalePath = "/idu/sdk-s8000/ING/${today.minusDays(5)}/stale.sit"

        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestUrls << args.url
            return [content: """
            {
              "items": [
                {"path": "${freshPath}", "lastModified": "${today}T00:00:00Z"},
                {"path": "${stalePath}", "lastModified": "${today.minusDays(5)}T00:00:00Z"}
              ],
              "continuationToken": null
            }
            """]
        })

        def result = script.call([
            pathPrefix: '/idu/sdk-s8000/ING/',
            daysBack: 2
        ])

        assert result == ['fresh': freshPath]
    }
}
