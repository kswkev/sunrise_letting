package com.sunriseletting.web.session;

import java.util.concurrent.CopyOnWriteArrayList;

import com.sunriseletting.web.security.AccountRole;
import com.sunriseletting.web.security.SessionPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.web.servlet.FlashMap;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

/**
 * Stores session attributes in Redis as JSON rather than with Java serialisation, so sessions stay
 * readable when the stored classes change between releases during a rolling update.
 */
@Configuration(proxyBeanMethods = false)
class SessionConfiguration {

    /** Spring Session picks this up by name as the serialiser for everything it writes to Redis. */
    @Bean
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // The Security modules only read types they trust. Anything else web keeps in the session must be listed here.
        BasicPolymorphicTypeValidator.Builder allowedTypes = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(CopyOnWriteArrayList.class)
                .allowIfSubType(FlashMap.class)
                .allowIfSubType(SessionPrincipal.class)
                .allowIfSubType(AccountRole.class);
        JsonMapper mapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader(), allowedTypes))
                // A session stored before a primitive field was added doesn't hold it, and must still be readable: it gets the default.
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        return new GenericJacksonJsonRedisSerializer(mapper);
    }
}
