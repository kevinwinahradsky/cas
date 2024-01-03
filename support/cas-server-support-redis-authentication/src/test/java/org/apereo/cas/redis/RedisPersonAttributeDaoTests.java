package org.apereo.cas.redis;

import org.apereo.cas.authentication.CasSSLContext;
import org.apereo.cas.authentication.principal.PrincipalResolver;
import org.apereo.cas.config.CasAuthenticationEventExecutionPlanTestConfiguration;
import org.apereo.cas.config.CasCoreAuthenticationConfiguration;
import org.apereo.cas.config.CasCoreAuthenticationPrincipalConfiguration;
import org.apereo.cas.config.CasCoreAuthenticationSupportConfiguration;
import org.apereo.cas.config.CasCoreAutoConfiguration;
import org.apereo.cas.config.CasCoreLogoutAutoConfiguration;
import org.apereo.cas.config.CasCoreNotificationsAutoConfiguration;
import org.apereo.cas.config.CasCoreServicesConfiguration;
import org.apereo.cas.config.CasCoreTicketsAutoConfiguration;
import org.apereo.cas.config.CasCoreUtilAutoConfiguration;
import org.apereo.cas.config.CasCoreWebAutoConfiguration;
import org.apereo.cas.config.CasPersonDirectoryConfiguration;
import org.apereo.cas.config.CasPersonDirectoryStubConfiguration;
import org.apereo.cas.config.CasRegisteredServicesTestConfiguration;
import org.apereo.cas.config.CasWebApplicationServiceFactoryConfiguration;
import org.apereo.cas.config.RedisAuthenticationConfiguration;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.redis.core.RedisObjectFactory;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.junit.EnabledIfListeningOnPort;
import lombok.val;
import org.apereo.services.persondir.IPersonAttributeDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link RedisPersonAttributeDaoTests}.
 *
 * @author Misagh Moayyed
 * @since 6.1.0
 */
@Tag("Redis")
@EnabledIfListeningOnPort(port = 6379)
@SpringBootTest(classes = {
    RefreshAutoConfiguration.class,
    WebMvcAutoConfiguration.class,
    RedisAuthenticationConfiguration.class,
    CasCoreAutoConfiguration.class,
    CasCoreLogoutAutoConfiguration.class,
    CasCoreNotificationsAutoConfiguration.class,
    CasCoreServicesConfiguration.class,
    CasCoreTicketsAutoConfiguration.class,
    CasCoreWebAutoConfiguration.class,
    CasPersonDirectoryConfiguration.class,
    CasPersonDirectoryStubConfiguration.class,
    CasCoreUtilAutoConfiguration.class,
    CasCoreAuthenticationConfiguration.class,
    CasCoreAuthenticationSupportConfiguration.class,
    CasRegisteredServicesTestConfiguration.class,
    CasWebApplicationServiceFactoryConfiguration.class,
    CasAuthenticationEventExecutionPlanTestConfiguration.class,
    CasCoreAuthenticationPrincipalConfiguration.class
},
    properties = {
        "cas.authn.attribute-repository.redis[0].host=localhost",
        "cas.authn.attribute-repository.redis[0].port=6379"
    })
@EnableConfigurationProperties(CasConfigurationProperties.class)
class RedisPersonAttributeDaoTests {
    private static final String USER_ID = UUID.randomUUID().toString();

    @Autowired
    @Qualifier(PrincipalResolver.BEAN_NAME_ATTRIBUTE_REPOSITORY)
    private IPersonAttributeDao attributeRepository;

    @Autowired
    private CasConfigurationProperties casProperties;

    @BeforeEach
    public void initialize() throws Exception {
        val redis = casProperties.getAuthn().getAttributeRepository().getRedis().getFirst();
        val conn = RedisObjectFactory.newRedisConnectionFactory(redis, true, CasSSLContext.disabled());
        val template = RedisObjectFactory.newRedisTemplate(conn);
        template.initialize();
        val attr = new HashMap<String, List<Object>>();
        attr.put("name", CollectionUtils.wrapList("John", "Jon"));
        attr.put("age", CollectionUtils.wrapList("42"));
        template.opsForHash().putAll(USER_ID, attr);
    }

    @Test
    void verifyAttributes() throws Throwable {
        val person = attributeRepository.getPerson(USER_ID);
        assertNotNull(person);
        val attributes = person.getAttributes();
        assertEquals(USER_ID, person.getName());
        assertTrue(attributes.containsKey("name"));
        assertTrue(attributes.containsKey("age"));
    }
}
