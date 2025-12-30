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
        helper.registerAllowedMethod('reportUsage', [String], { String path -> })
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
}

