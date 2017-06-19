package net.sourceforge.extension.controller;

import com.mysema.commons.lang.Pair;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Enumeration;
import java.util.UUID;

/**
 * Created by c.karalis on 30/08/2016.
 */
public class AbstractControllerTest {

//    protected static final Logger LOGGER = Logger.getLogger(AbstractControllerTest.class);

    String user = "admin";
    MockHttpSession mockHttpSession;
    MockMvc mockMvc;

    @Autowired
    WebApplicationContext applicationContext;

    @Before
    public void init() throws Exception {
        mockHttpSession = new MockHttpSession(applicationContext.getServletContext(), UUID.randomUUID().toString());
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).
                build();

    }

    protected ResultActions getAndRespond(String urlTemplate, Pair<String, String>... params) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get(urlTemplate).header("Content-type", "application/json").session(mockHttpSession);
        Enumeration<String> names = mockHttpSession.getAttributeNames();
        for (Pair<String, String> param : params) {
            requestBuilder.param(param.getFirst(), param.getSecond());
        }
        return mockMvc.perform(requestBuilder);
    }

    protected ResultActions delete(String urlTemplate) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete(urlTemplate).header("Content-type", "application/json").session(mockHttpSession));
    }

    protected ResultActions post(String content, String urlTemplate) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post(urlTemplate).content(content).header("Content-type", "application/json").session(mockHttpSession));
    }

    protected ResultActions upload(String urlTemplate, MockMultipartFile ... files) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = MockMvcRequestBuilders.fileUpload(urlTemplate);
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        return mockMvc.perform(builder.session(mockHttpSession));
    }

    protected MockHttpServletResponse patchAndRespond(String content, String urlTemplate) throws Exception {
//        LOGGER.info(content);
        return patch(content, urlTemplate)
                .andReturn().getResponse();
    }

    protected ResultActions patch(String content, String urlTemplate) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.patch(urlTemplate).content(content).header("Content-type", "application/json").session(mockHttpSession));
    }

}
