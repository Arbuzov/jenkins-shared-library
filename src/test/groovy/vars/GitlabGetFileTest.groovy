package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class GitlabGetFileTest extends BasePipelineTest {

    private static final String MANIFEST =
        "name: nms-single-meta\r\nversion: 1.1.0\r\nplugins:\r\n  nms-ng: 2.0.3-b69-offline\r\n"

    private static Map fileResponse(String text) {
        return [
            file_name: 'Manifest',
            file_path: 'Manifest',
            size: text.getBytes('UTF-8').length,
            encoding: 'base64',
            content: text.getBytes('UTF-8').encodeBase64().toString(),
            ref: 'master',
            blob_id: 'b10b1d',
            commit_id: 'c0ffee1234',
            last_commit_id: '1a5tc0',
        ]
    }

    def script
    def apiCalls
    def apiResponse
    def apiFailure

    @Before
    void setUp() {
        super.setUp()
        binding.setVariable('env', [:])
        apiCalls = []
        apiResponse = fileResponse(MANIFEST)
        apiFailure = null

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('gitlabApiGet', [Map], { Map args ->
            apiCalls << args
            if (apiFailure) {
                throw new RuntimeException(apiFailure)
            }
            return apiResponse
        })

        script = loadScript('vars/gitlabGetFile.groovy')
    }

    // ---------- request ----------

    @Test
    void readsTheFileAtTheRefThroughGitlabApiGet() {
        script.call([filePath: 'Manifest', ref: 'master'])

        assert apiCalls.size() == 1
        assert apiCalls[0] == [path: 'repository/files/Manifest', query: [ref: 'master']]
    }

    @Test
    void encodesTheSlashesAndSpacesOfTheFilePath() {
        script.call([filePath: 'deploy/my file.yml', ref: 'master'])

        assert apiCalls[0].path == 'repository/files/deploy%2Fmy%20file.yml'
        assert apiCalls[0].query == [ref: 'master']
    }

    @Test
    void passesTheRefUnencodedForGitlabApiGetToEncode() {
        script.call([filePath: 'Manifest', ref: 'release/v.1.1.0'])

        assert apiCalls[0].query == [ref: 'release/v.1.1.0']
    }

    @Test
    void passesTheProjectAndCredentialArgsThrough() {
        script.call([
            filePath: 'Manifest',
            ref: 'master',
            gitUrl: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git',
            projectId: 412,
            gitlabBaseUrl: 'https://gitlab.example.com',
            credentialsId: 'other-token',
            debug: true,
        ])

        assert apiCalls[0] == [
            gitUrl: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git',
            projectId: 412,
            gitlabBaseUrl: 'https://gitlab.example.com',
            credentialsId: 'other-token',
            debug: true,
            path: 'repository/files/Manifest',
            query: [ref: 'master'],
        ]
    }

    @Test
    void keepsFilePathRefAndUnknownKeysOutOfTheGitlabApiGetArgs() {
        script.call([filePath: 'Manifest', ref: 'master', recursive: true])

        assert !apiCalls[0].containsKey('filePath')
        assert !apiCalls[0].containsKey('ref')
        assert !apiCalls[0].containsKey('recursive')
    }

    // ---------- result ----------

    @Test
    void decodesACrlfFileIntact() {
        def file = script.call([filePath: 'Manifest', ref: 'master'])

        assert file.text == MANIFEST
        assert file.text.contains('\r\n  nms-ng: 2.0.3-b69-offline\r\n')
    }

    @Test
    void keepsTheFieldsOfGitlabsAnswer() {
        def file = script.call([filePath: 'Manifest', ref: 'master'])

        assert file.commit_id == 'c0ffee1234'
        assert file.last_commit_id == '1a5tc0'
        assert file.content == apiResponse.content
        assert file.encoding == 'base64'
        assert file.file_path == 'Manifest'
    }

    @Test
    void decodesTheContentAsUtf8() {
        String umlauts = 'title: Überprüfung\n'
        apiResponse = fileResponse(umlauts)

        def file = script.call([filePath: 'Manifest', ref: 'master'])

        assert file.text == umlauts
    }

    @Test
    void decodesContentWrappedOverSeveralLines() {
        String wrapped = MANIFEST.getBytes('UTF-8').encodeBase64(true).toString()
        assert wrapped.contains('\n')
        apiResponse = fileResponse(MANIFEST) + [content: wrapped]

        def file = script.call([filePath: 'Manifest', ref: 'master'])

        assert file.text == MANIFEST
    }

    @Test
    void returnsAnEmptyTextForAnEmptyFile() {
        apiResponse = fileResponse('')

        def file = script.call([filePath: 'Manifest', ref: 'master'])

        assert file.text == ''
    }

    // ---------- validation ----------

    @Test
    void failsWithoutARefAndMakesNoRequest() {
        try {
            script.call([filePath: 'Manifest'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("gitlabGetFile(): 'ref' is required")
        }
        assert apiCalls.isEmpty()
    }

    @Test
    void failsWithoutAFilePathAndMakesNoRequest() {
        try {
            script.call([ref: 'master'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("gitlabGetFile(): 'filePath' is required")
        }
        assert apiCalls.isEmpty()
    }

    @Test
    void letsAnErrorOfGitlabApiGetSurface() {
        apiFailure = 'gitlabApiGet(): GET https://gitlab.example.com/api/v4/projects/412/repository/files/' +
            'Manifest?ref=nope answered 404.'

        try {
            script.call([filePath: 'Manifest', ref: 'nope'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('answered 404')
        }
    }
}
