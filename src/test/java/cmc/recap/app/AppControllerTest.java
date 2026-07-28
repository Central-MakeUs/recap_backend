package cmc.recap.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AppControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppVersionService appVersionService;

    @Test
    @DisplayName("인증 헤더 없이 호출해도 200을 응답한다")
    void 인증_헤더_없이_호출해도_200을_응답한다() throws Exception {
        given(appVersionService.checkVersion(any(), any()))
                .willReturn(VersionCheckResponse.of(false, "1.0.0", ""));

        mockMvc.perform(get("/api/v1/app/version-check")
                        .param("platform", "IOS")
                        .param("version", "1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("파라미터 없이 호출해도 500이 아니라 정상 폴백 응답을 반환한다")
    void 파라미터_없이_호출해도_정상_폴백_응답을_반환한다() throws Exception {
        given(appVersionService.checkVersion(null, null))
                .willReturn(VersionCheckResponse.of(false, null, null));

        mockMvc.perform(get("/api/v1/app/version-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.forceUpdate").value(false));
    }
}
