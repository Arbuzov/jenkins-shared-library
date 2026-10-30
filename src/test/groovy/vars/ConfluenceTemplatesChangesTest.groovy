package vars

import groovy.text.SimpleTemplateEngine
import org.junit.Test

// Renders the "Changes" section of the Confluence templates. A change set gets no
// repository browser when the SCM has none configured and the Git plugin cannot
// guess one (it only guesses for github.com), e.g. a GitLab checkout over SSH.
class ConfluenceTemplatesChangesTest {

    private static final List<String> RELEASE_TEMPLATES = [
        'deb-package.release.gtpl',
        'docker.release.gtpl',
        'application-plugin.release.gtpl',
        'image.release.gtpl',
    ]

    static class FakeChangeSet extends ArrayList {
        def browser
    }

    private static Map entry() {
        [
            revision    : '0123456789abcdef0123456789abcdef01234567',
            msgAnnotated: 'PROJ-1: Fix the build',
            author      : [url: 'user/jane.doe', displayName: 'jane.doe'],
        ]
    }

    private static FakeChangeSet changeSet(browser) {
        def changeSet = new FakeChangeSet(browser: browser)
        changeSet << entry()
        changeSet
    }

    private static Expando gitlabBrowser() {
        new Expando(
            getChangeSetLink: { c -> new URL("https://gitlab.example.com/commit/${c.revision}") },
            descriptor: [displayName: 'GitLab'],
        )
    }

    private static Expando build(List changeSets) {
        new Expando(
            number: 27,
            url: 'job/release_1.1.x/27/',
            displayName: '1.1.35-b27',
            changeSets: changeSets,
            getAbsoluteUrl: { -> 'https://jenkins.example.com/job/release_1.1.x/27/' },
            getBuildCauses: { -> [[shortDescription: 'Started by user']] },
        )
    }

    // The rest of the page the templates read unconditionally; only the Changes section is checked.
    private static final Map PAGE_VARS = [
        version          : '1.1.35-b27',
        projectId        : 'PHS',
        packageName      : 'dataplane-phs-ng',
        imageUri         : '',
        date             : '',
        images           : '',
        pluginUrl        : '',
        fwUrl            : '',
        isoUrl           : '',
        installationPage : '',
        installationSpace: '',
        keyComponents    : '',
    ]

    private static String render(String name, Map vars) {
        def text = new File("resources/com/spacebridge/confluence/${name}").getText('UTF-8')
        new SimpleTemplateEngine().createTemplate(text).make(PAGE_VARS + vars).toString()
    }

    @Test
    void releaseTemplatesFallBackToTheDetailLinkWithoutARepositoryBrowser() {
        RELEASE_TEMPLATES.each { name ->
            def page = render(name, [build: build([changeSet(null)])])

            assert page.contains('PROJ-1: Fix the build') : name
            assert page.contains("href='27/changes#detail0'>Detail</a>") : name
        }
    }

    @Test
    void releaseTemplatesLinkTheCommitThroughTheRepositoryBrowser() {
        RELEASE_TEMPLATES.each { name ->
            def page = render(name, [build: build([changeSet(gitlabBrowser())])])

            assert page.contains("href='https://gitlab.example.com/commit/0123456789abcdef0123456789abcdef01234567'>GitLab</a>") : name
            assert !page.contains('>Detail</a>') : name
        }
    }

    @Test
    void buildChangesTemplateFallsBackToTheDetailLinkWithoutARepositoryBrowser() {
        def page = render('jenkins-build-changes.gtpl', [
            build             : build([]),
            changeSets        : [changeSet(null), changeSet(gitlabBrowser())],
            headerLevel       : '3',
            rootURL           : 'https://jenkins.example.com',
            formattedTimestamp: '2026-10-30 13:53',
        ])

        assert page.contains("href='27/changes#detail0'>Detail</a>")
        assert page.contains("href='https://gitlab.example.com/commit/0123456789abcdef0123456789abcdef01234567'>GitLab</a>")
    }
}
