package com.example.miniProj.service;

import com.example.miniProj.domain.Member;
import com.example.miniProj.domain.dao.GetMemberMapper;
import com.example.miniProj.domain.dao.PostMemberMapper;
import com.example.miniProj.domain.dto.*;
import com.example.miniProj.util.AESCipher;
import com.example.miniProj.util.Constants;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

import static com.example.miniProj.util.Constants.FIRST_REGISTER;
import static com.example.miniProj.util.Constants.RE_REGISTER;

@Service
@RequiredArgsConstructor
@Builder
@Slf4j
public class MemberServiceImpl implements MemberService {

    private final GetMemberMapper getMemberMapper;
    private final PostMemberMapper postMemberMapper;

    @Override
    public void joinMember(CertificationRequestDto certificationRequestDto) {
        postMemberMapper.saveMember(certificationRequestDto);
    }

    @Override
    public VerificationRequestDto getResult(String certTxId) {
        return getMemberMapper.getResult(certTxId);
    }

    @Override
    public MemberResultDto findMember(String certTxId) {
        return getMemberMapper.findMember(certTxId);
    }

    @Override
    public CertificationRequestDto findCertification(String certTxId) {
        return getMemberMapper.findCertification(certTxId);
    }

    @Override
    public CertificationRequestDto setCertification(MemberDto memberDto) throws Exception {

        return CertificationRequestDto.builder()
                .companyCd(Constants.COMPANYCD)
                .serviceTycd(Constants.SERVICETYCD_LIST.get(3))
                .reqTitle(Constants.REQ_TITLE)
                .reqCSPhoneNo(Constants.REQ_CSPHONE_NO)
                .reqEndDttm(createExpirationTime())
                .signTargetTycd("4")
                .signTarget(Constants.SIGN_TARGET)
                .reqTxId(createReqTxId())
                .isCombineAuth("N")
                .isDigitalSign("Y")
                .isNotification("Y")
                .isPASSVerify("Y")
                .isUserAgreement("N")
                .memberDto(parsingMemberDto(memberDto))
                .build();
    }

    @Override
    public MemberResultDto setMemberResult(MemberResultDto memberResultDto) throws Exception {

        AESCipher aesCipher = new AESCipher(Constants.ENCRYPT_KEY);
//        RsaDecrypt rsaDecrypt = new RsaDecrypt();
        String decryptCI = aesCipher.decrypt(memberResultDto.getCi());

        return MemberResultDto.builder()
                .reqTxId(memberResultDto.getReqTxId())
                .telcoTxId(memberResultDto.getTelcoTxId())
                .certTxId(memberResultDto.getCertTxId())
                .resultTycd(memberResultDto.getResultTycd())
                .digitalSign(memberResultDto.getDigitalSign())
                .ci(memberResultDto.getCi())
                .userNm(memberResultDto.getUserNm())
                .birthday(memberResultDto.getBirthday())
                .gender(memberResultDto.getGender())
                .decryptedUserNm(aesCipher.decrypt(memberResultDto.getUserNm()))
                .decryptedBirthday(aesCipher.decrypt(memberResultDto.getBirthday()))
                .decryptedGender(aesCipher.decrypt(memberResultDto.getGender()))
                .decryptedCI(decryptCI)
                .build();
    }

    @Override
    public CertificationRequestDto resetReqEndDttm(CertificationRequestDto certificationRequestDto) {
        certificationRequestDto.setReqEndDttm(createExpirationTime());
        return certificationRequestDto;
    }

    @Override
    public void saveCertTxId(CertificationResponseDto certificationResponseDto) {
        postMemberMapper.saveCertTxId(certificationResponseDto);
    }

    @Override
    public void updateMemberByReRegister(CertificationResponseDto certificationResponseDto, String reReqEndDttm) {
        postMemberMapper.updateMemberByReRegister(certificationResponseDto, reReqEndDttm);
    }

    @Override
    public void saveVerifyResult(VerifyResultResponseDto verifyResultResponseDto) {
        postMemberMapper.saveVerifyResult(verifyResultResponseDto);
    }

    private MemberDto parsingMemberDto(MemberDto memberDto) throws Exception {
        memberDto.setGender(Member.initGender(memberDto.getGender(), memberDto.getBirthday()));
        memberDto.setTelcoTyCd(Member.initTelcoTycd(memberDto.getTelcoTyCd()));
        memberDto.setPhoneNo(Member.initPhoneNo(memberDto.getPhoneNo()));
        return encryptMemberDto(memberDto);
    }

    private String createExpirationTime() { //현재시간 기준 5분 이후
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = Calendar.getInstance().getTime();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MINUTE, 5);

        return simpleDateFormat.format(cal.getTime());
    }

    private String createReqTxId() { //타임스태프로 20자리 값 요청(특수문자 사용 X)
        LocalDateTime localDateTime = LocalDateTime.now();
        String timeStamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(localDateTime);

        return timeStamp + String.valueOf(generateAuthNo());
    }

    private int generateAuthNo() {
        return ThreadLocalRandom.current().nextInt(100000, 1000000);
    }

    private MemberDto encryptMemberDto(MemberDto memberDto) throws Exception {
        AESCipher aesCipher = new AESCipher(Constants.ENCRYPT_KEY);

        return MemberDto.builder()
                .telcoTyCd(memberDto.getTelcoTyCd())
                .phoneNo(aesCipher.encrypt(memberDto.getPhoneNo()))
                .userNm(aesCipher.encrypt(memberDto.getUserNm()))
                .birthday(aesCipher.encrypt(memberDto.getBirthday()))
                .gender(aesCipher.encrypt(memberDto.getGender()))
                .build();
    }

    @Override
    public CertificationResponseDto postHttpClientByLogin(CertificationRequestDto certificationRequestDto,
                                                          String _url, String registerStatus) throws Exception {

        String entityBody = "";

        CloseableHttpClient client = HttpClientBuilder.create().build(); // HttpClient 생성
        HttpPost postRequest = new HttpPost(_url); //POST 메소드 URL 새성

        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(1 * 1000)
                .setConnectTimeout(1 * 1000)
                .setConnectionRequestTimeout(1 * 1000)
                .build();
        postRequest.setConfig(requestConfig);

//        try {
            postRequest.setHeader("Accept", "application/json");
            postRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
            postRequest.addHeader("Authorization", "Bearer " + Constants.ACCESS_TOKEN);

            postRequest.setEntity(new StringEntity(getJsonObjectByLogin(certificationRequestDto).toString(), ContentType.APPLICATION_JSON)); //json 메시지 입력

            CloseableHttpResponse response = client.execute(postRequest);
//            System.out.println("try2:" + response.toString());
            //Response 출력
            entityBody = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                ResponseHandler<String> handler = new BasicResponseHandler();
//                String body = handler.handleResponse(response);

                System.out.println("httpClient(LOGIN) msg: " + entityBody);

                ObjectMapper objectMapper = new ObjectMapper();
                CertificationResponseDto certificationResponseDto =
                        objectMapper.readValue(entityBody, CertificationResponseDto.class);

                if(registerStatus.equals(FIRST_REGISTER)) {
                    System.out.println("first register: " + certificationResponseDto);
                    saveCertTxId(certificationResponseDto);
                } else if (registerStatus.equals(RE_REGISTER)) {
                    System.out.println("reRegister: " + certificationResponseDto);
                    updateMemberByReRegister(certificationResponseDto, certificationRequestDto.getReqEndDttm());
                }

                return certificationResponseDto;
            } else {
                System.out.println("Certification Response is error : " + response.getStatusLine().getStatusCode());
                System.out.println("else " + entityBody);
                throw new Exception(entityBody);
            }

    }

    @Override
    public VerifyResultResponseDto postHttpClientByRegister(VerificationRequestDto verificationRequestDto,
                                                            String _url) throws Exception {
        String entityBody = "";

//        try {
            CloseableHttpClient client = HttpClientBuilder.create().build(); // HttpClient 생성
            HttpPost postRequest = new HttpPost(_url); //POST 메소드 URL 새성
            postRequest.setHeader("Accept", "application/json");
            postRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
//            postRequest.addHeader("Authorization", "Bearer " + StageServer.ACCESS_TOKEN);

            System.out.println("Service Layer: " + verificationRequestDto);
            postRequest.setEntity(new StringEntity(getJsonObjectByRegister(verificationRequestDto).toString(), ContentType.APPLICATION_JSON)); //json 메시지 입력

            CloseableHttpResponse response = client.execute(postRequest);

            ResponseHandler<String> handler = new BasicResponseHandler();

            entityBody = EntityUtils.toString(response.getEntity());

//            String body = handler.handleResponse(response);
//            System.out.println(response.getStatusLine().getStatusCode());
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

                System.out.println("httpClient(REGISTER) msg: " + entityBody);

                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                VerifyResultResponseDto verifyResultResponseDto =
                        objectMapper.readValue(entityBody, VerifyResultResponseDto.class);
                saveVerifyResult(verifyResultResponseDto);

                return verifyResultResponseDto;

            } else {
                System.out.println("Verification Response is error : " + response.getStatusLine().getStatusCode());
                System.out.println("else " + entityBody);
                throw new Exception(entityBody);
            }
//        } catch (Exception e) {
//            System.out.println("catch: " + e.getMessage());
//            throw new Exception(entityBody);
//        }
    }

    private static JSONObject getJsonObjectByLogin(CertificationRequestDto certificationDto) {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("companyCd", certificationDto.getCompanyCd());
        jsonObject.put("serviceTycd", certificationDto.getServiceTycd());
        jsonObject.put("telcoTycd", certificationDto.getMemberDto().getTelcoTyCd());
        jsonObject.put("phoneNo", certificationDto.getMemberDto().getPhoneNo());
        jsonObject.put("userNm", certificationDto.getMemberDto().getUserNm());
        jsonObject.put("birthday", certificationDto.getMemberDto().getBirthday());
        jsonObject.put("gender", certificationDto.getMemberDto().getGender());
        jsonObject.put("reqTitle", certificationDto.getReqTitle());
        jsonObject.put("reqCSPhoneNo", certificationDto.getReqCSPhoneNo());
        jsonObject.put("reqEndDttm", certificationDto.getReqEndDttm());
        jsonObject.put("isNotification", certificationDto.getIsNotification());
        jsonObject.put("isPASSVerify", certificationDto.getIsPASSVerify());
        jsonObject.put("signTargetTycd", certificationDto.getSignTargetTycd());
        jsonObject.put("signTarget", certificationDto.getSignTarget());
        jsonObject.put("isUserAgreement", certificationDto.getIsUserAgreement());
        jsonObject.put("reqTxId", certificationDto.getReqTxId());
        jsonObject.put("isDigitalSign", certificationDto.getIsDigitalSign());

        return jsonObject;
    }

    private static JSONObject getJsonObjectByRegister(VerificationRequestDto verificationRequestDto) {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("companyCd", verificationRequestDto.getCompanyCd());
        jsonObject.put("reqTxId", verificationRequestDto.getReqTxId());
        jsonObject.put("certTxId", verificationRequestDto.getCertTxId());
        jsonObject.put("phoneNo", verificationRequestDto.getPhoneNo());
        jsonObject.put("userNm", verificationRequestDto.getUserNm());
        jsonObject.put("signTarget", verificationRequestDto.getSignTarget());

        return jsonObject;
    }
}
