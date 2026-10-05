package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class GetRepReproArtifactsTest extends BasePipelineTest {

    static class HttpResponseMock {
        String content
        String getContent() { return content }
    }

    def script
    def lastHttpRequestArgs
    def echoMessages = []

    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoMessages << message })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return new HttpResponseMock(content: '')
        })
        script = loadScript('vars/getRepReproArtifacts.groovy')
    }

    @Test
    void returnsUniqueSortedVersionsForPackage() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return new HttpResponseMock(content: (
                "Package: other\nVersion: 9.9.9\n\n" +
                "Package: my-package\nVersion: 1.0.1\n\n" +
                "Package: my-package\nVersion: 1.0.0\n\n" +
                "Package: my-package\nVersion: 1.0.1\n"
            ))
        })

        def result = script.call([
            repository: 'stable',
            packageName: 'my-package',
            nexusURL: 'http://nexus.example.com/repository/debian'
        ])

        assert lastHttpRequestArgs.url == 'http://nexus.example.com/repository/debian/dists/stable/main/binary-amd64/Packages'
        assert result == ['1.0.0', '1.0.1']
        assert echoMessages.isEmpty()
    }

    @Test
    void ignoresPackagesWhoseNameMerelyStartsWithTheRequestedOne() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return new HttpResponseMock(content: (
                "Package: ncc\nVersion: 5.2.20\n\n" +
                "Package: ncc-tools\nVersion: 1.1.1\n\n" +
                "Package: nccdev\nVersion: 2.2.2\n\n" +
                "Package: libncc\nVersion: 3.3.3\n"
            ))
        })

        def result = script.call([repository: 'stable', packageName: 'ncc'])

        assert result == ['5.2.20']
    }

    @Test
    void batchReturnsAVersionListPerPackageFromASingleFetch() {
        def fetches = 0
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            fetches++
            lastHttpRequestArgs = args
            return new HttpResponseMock(content: (
                "Package: ncc\nVersion: 5.2.20\n\n" +
                "Package: ncc-tools\nVersion: 1.1.1\n\n" +
                "Package: ulpc\nVersion: 5.2.11\n\n" +
                "Package: ulpc\nVersion: 5.2.10\n\n" +
                "Package: unwanted\nVersion: 9.9.9\n"
            ))
        })

        def result = script.call([repository: 'stable', packageNames: ['ncc', 'ulpc', 'absent']])

        assert fetches == 1
        assert result == [ncc: ['5.2.20'], ulpc: ['5.2.10', '5.2.11'], absent: []]
    }

    @Test
    void batchReturnsEmptyListsWhenHttpRequestFails() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            throw new RuntimeException('boom')
        })

        def result = script.call([repository: 'stable', packageNames: ['ncc', 'ulpc']])

        assert result == [ncc: [], ulpc: []]
        assert echoMessages[0].contains('ncc, ulpc')
    }

    @Test
    void returnsEmptyListWhenHttpRequestFails() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            throw new RuntimeException('boom')
        })

        def result = script.call([
            repository: 'stable',
            packageName: 'my-package',
            nexusURL: 'http://nexus.example.com/repository/debian'
        ])

        assert result == []
        assert echoMessages.size() == 1
        assert echoMessages[0].contains('Warning: Could not fetch versions for my-package: boom')
    }

    @Test
    void strictModePropagatesAnIndexFailure() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            throw new RuntimeException('index unavailable')
        })
        try {
            script.call(repository: 'bullseye', packageNames: ['ncc'], failOnError: true)
            assert false: 'an unavailable index must fail the configurator'
        } catch (RuntimeException expected) {
            assert expected.message == 'index unavailable'
        }
        assert echoMessages == []
    }
}

