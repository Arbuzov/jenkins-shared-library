package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class DownloadConfluenceAttachmentsTest extends BasePipelineTest {

    def script
    def httpCalls = []
    String attachmentsJson
    String pageLookupJson

    @Before
    void setUp() {
        super.setUp()
        httpCalls = []
        pageLookupJson = JsonOutput.toJson([results: [[id: '42']]])
        attachmentsJson = JsonOutput.toJson([
            results: [
                [title: 'report.pdf', _links: [download: '/download/attachments/42/report.pdf']],
                [title: 'checksums.txt', _links: [download: '/download/attachments/42/checksums.txt']]
            ],
            _links: [:]
        ])

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            httpCalls << args

            if ((args.url as String).contains('/content?')) {
                return [status: 200, content: pageLookupJson]
            }
            if ((args.url as String).contains('/child/attachment')) {
                return [status: 200, content: attachmentsJson]
            }
            if (args.outputFile) {
                return [status: 200, content: '']
            }

            throw new RuntimeException("Unexpected httpRequest URL: ${args.url}")
        })

        script = loadScript('vars/downloadConfluenceAttachments.groovy')
    }

    @Test
    void downloadsAllAttachmentsWhenFilterNotProvided() {
        def tempDir = createTempDir()

        def result = script.call([
            PAGE_ID   : '42',
            SAVE_PATH : tempDir.path
        ])

        assert result.pageId == '42'
        assert result.downloaded.size() == 2
        assert result.missing.isEmpty()

        def downloadCalls = httpCalls.findAll { it.outputFile }
        assert downloadCalls.size() == 2
        assert downloadCalls[0].url == 'https://confluence.spacebridge.com/download/attachments/42/report.pdf'
        assert downloadCalls[1].url == 'https://confluence.spacebridge.com/download/attachments/42/checksums.txt'
    }

    @Test
    void resolvesPageByNameAndDownloadsSelectedAttachmentsWithRename() {
        def tempDir = createTempDir()

        def result = script.call([
            CONFLUENCE_API : 'https://confluence.example.com/wiki/rest/api',
            SPACE          : 'DOC',
            PAGE_NAME      : 'Release Notes',
            SAVE_PATH      : tempDir.path,
            ATTACHMENTS    : ['report.pdf': 'docs/release-report.pdf']
        ])

        assert result.pageId == '42'
        assert result.downloaded.size() == 1
        assert result.downloaded[0].source == 'report.pdf'
        assert result.downloaded[0].url == 'https://confluence.example.com/wiki/download/attachments/42/report.pdf'
        assert result.downloaded[0].path.endsWith('docs/release-report.pdf'.replace('/', File.separator))
    }

    @Test
    void failsWhenRequestedAttachmentIsMissingAndAllowMissingDisabled() {
        def tempDir = createTempDir()

        try {
            script.call([
                PAGE_ID      : '42',
                SAVE_PATH    : tempDir.path,
                ATTACHMENTS  : ['missing.txt']
            ])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException e) {
            assert e.message.contains('Requested attachment(s) not found: missing.txt')
        }
    }

    @Test
    void returnsMissingWhenAllowMissingEnabled() {
        def tempDir = createTempDir()

        def result = script.call([
            PAGE_ID        : '42',
            SAVE_PATH      : tempDir.path,
            ATTACHMENTS    : ['missing.txt'],
            ALLOW_MISSING  : true
        ])

        assert result.downloaded.isEmpty()
        assert result.missing == ['missing.txt']
    }

    @Test
    void skipsExistingFilesWhenOverwriteDisabled() {
        def tempDir = createTempDir()
        def existing = new File(tempDir, 'report.pdf')
        existing.text = 'already here'

        def result = script.call([
            PAGE_ID      : '42',
            SAVE_PATH    : tempDir.path,
            ATTACHMENTS  : ['report.pdf'],
            OVERWRITE    : false
        ])

        assert result.downloaded.isEmpty()
        assert result.skipped.size() == 1
        assert result.skipped[0].reason == 'already exists'
    }

    private File createTempDir() {
        File dir = File.createTempFile('confluence-', '-attachments')
        dir.delete()
        dir.mkdirs()
        return dir
    }
}
