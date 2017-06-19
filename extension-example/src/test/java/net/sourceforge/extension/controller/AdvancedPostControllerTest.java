package net.sourceforge.extension.controller;

import net.sourceforge.extension.support.RestDataConfig;
import net.sourceforge.extension.support.WebConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.hamcrest.Matchers;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Created by christos.karalis on 6/10/2017.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
        classes = {RestDataConfig.class, WebConfig.class}
)
@WebAppConfiguration
public class AdvancedPostControllerTest extends AbstractControllerTest {

    private String applicant1 = "http://localhost/applicant/1";
    private String applicant2 = "http://localhost/applicant/2";

    @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    @Test
    public void testPost() throws Exception {

        ResultActions post = post("{ \"applicant\" : { \"id\" : 1, \"name\" : \"David\", \"surname\" : \"Costa\"} } }", "/applicant/advanced");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.CREATED.value()))
                .andExpect(MockMvcResultMatchers.header().string("Location", applicant1));

        post = post("{ \"operator\" : \"AND\" }", "/applicant/search/advanced");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.OK.value()))
                .andExpect(MockMvcResultMatchers.jsonPath("page.totalElements", Matchers.is(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("_embedded.applicants[0].name", Matchers.is("David")))
                .andExpect(MockMvcResultMatchers.jsonPath("_embedded.applicants[0].surname", Matchers.is("Costa")));

        post = patch("{ \"applicant\" : { \"id\" : 1, \"name\" : \"Jorge\", \"surname\" : \"Costa\"} } }", "/applicant/advanced/1");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.CREATED.value()));

        post = post("{ \"operator\" : \"AND\" }", "/applicant/search/advanced");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.OK.value()))
                .andExpect(MockMvcResultMatchers.jsonPath("page.totalElements", Matchers.is(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("_embedded.applicants[0].name", Matchers.is("Jorge")));

        post = post("{ \"applicant\" : { \"id\" : 2, \"name\" : \"Enrique\", \"surname\" : \"Mendoza\"} } }", "/applicant/advanced");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.CREATED.value()))
                .andExpect(MockMvcResultMatchers.header().string("Location", applicant2));

        post = post("{ \"operator\" : \"AND\" }", "/applicant/search/advanced");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.OK.value()))
                .andExpect(MockMvcResultMatchers.jsonPath("page.totalElements", Matchers.is(2)));
    }

    @Sql(scripts = "classpath:services.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    @Test
    public void testAssociation() throws Exception {
        ResultActions post = post("{ \"application\" : { \"id\" : 1, \"applicant\" : \""+applicant1+"\"}, \"orderLines\" : [{ \"quantity\" : 2, \"service\" : \"http://localhost/service/1\"}] }", "/application/advanced");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.CREATED.value()));

        post = getAndRespond("/application/1/orderLines");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.OK.value()))
                .andExpect(MockMvcResultMatchers.jsonPath("_embedded.orderLines", Matchers.hasSize(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("_embedded.orderLines[0].quantity", Matchers.is(2)))
                .andExpect(MockMvcResultMatchers.jsonPath("_embedded.orderLines[0]._links.service.href", Matchers.is("http://localhost/orderLine/1/service")));

        post = getAndRespond("/orderLine/1/service");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.OK.value()))
                .andExpect(MockMvcResultMatchers.jsonPath("name", Matchers.is("SERVICE_A")));

        post = patch("{ \"application\" : { \"applicant\" : \""+applicant1+"\"}, \"orderLines\" : [{ \"quantity\" : 3, \"service\" : \"http://localhost/service/2\"}] }", "/application/advanced/1");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.CREATED.value()));

        post = getAndRespond("/application/1/orderLines");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.OK.value()))
                .andExpect(MockMvcResultMatchers.jsonPath("_embedded.orderLines", Matchers.hasSize(1)))
                .andExpect(MockMvcResultMatchers.jsonPath("_embedded.orderLines[0].quantity", Matchers.is(3)))
                .andExpect(MockMvcResultMatchers.jsonPath("_embedded.orderLines[0]._links.service.href", Matchers.is("http://localhost/orderLine/2/service")));


        post = getAndRespond("/orderLine/2/service");
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.OK.value()))
                .andExpect(MockMvcResultMatchers.jsonPath("name", Matchers.is("SERVICE_B")));

        System.out.println(getAndRespond("/application/1").andReturn().getResponse().getContentAsString());


    }


    @Sql(scripts = "classpath:services.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "classpath:drop.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    @Test
    public void testPostFile() throws Exception {
        InputStream fi1 = new ByteArrayInputStream(new String("text").getBytes());
        MockMultipartFile upload = new MockMultipartFile("upload", "file.txt", "multipart/form-data", fi1);

        InputStream payload = new ByteArrayInputStream(new String("{ \"application\" : { \"id\" : 1, \"applicant\" : \""+applicant1+"\"}, \"orderLines\" : [{ \"quantity\" : 2, \"service\" : \"http://localhost/service/1\"}] }").getBytes());
        MockMultipartFile uploadPayload = new MockMultipartFile("payload", "payload.json", "multipart/form-data", payload);


        ResultActions post = upload("/application/advanced", upload, uploadPayload);
        post.andExpect(MockMvcResultMatchers.status().is(HttpStatus.CREATED.value()));

        System.out.println(getAndRespond("/application/1").andReturn().getResponse().getContentAsString());
    }
}
