package com.helianhealth.agent.repository.mapper.hems;

import org.apache.ibatis.annotations.Select;

public interface HemsMapper {
    @Select("SELECT PatientCode as patientCode " +
            "FROM VocaPatient " +
            "WHERE VisitNo = #{visitNo}")
    String selectPatientCodeByVisitNo(String visitNo);

    @Select("select vp.PatientCode as PatientCode " +
            "from VocaPatientFeeItem as vpfi " +
            "inner join VocaPatient as vp on vp.ID_Patient = vpfi.ID_Patient " +
            "where vpfi.applyId = #{applyId}")
    String selectPatientCodeByApplyId(String applyId);

    @Select("select id_patientfeeitem " +
            "from VocaPatientFeeItem " +
            "where applyId = #{applyId}")
    String selectPatientFeeItemIdByApplyId(String applyId);
}
