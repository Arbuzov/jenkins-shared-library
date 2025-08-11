package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class NetboxFunctionsTest extends BasePipelineTest {
    @Before
    void setUp() {
        super.setUp()
        // Мокаем httpRequest и readJSON
        helper.registerAllowedMethod('httpRequest', [Map], { args ->
            return [content: '{"results": ["item1", "item2"]}']
        })
        helper.registerAllowedMethod('readJSON', [Map], { args ->
            return [results: ['item1', 'item2']]
        })
        helper.registerAllowedMethod('withCredentials', [List, Closure], { creds, body ->
            binding.setVariable('TOKEN', 'fake-token')
            body()
        })
    }

    @Test
    void testGetNetboxDevices() {
        def result = loadScript('vars/getNetboxDevices.groovy').call([role: 'server', tenant: 'test'])
        assert result == ['item1', 'item2']
    }

    @Test
    void testGetNetboxRacks() {
        def result = loadScript('vars/getNetboxRacks.groovy').call([:])
        assert result == ['item1', 'item2']
    }

    @Test
    void testGetNetboxTenants() {
        def result = loadScript('vars/getNetboxTenants.groovy').call([:])
        assert result == ['item1', 'item2']
    }
}
