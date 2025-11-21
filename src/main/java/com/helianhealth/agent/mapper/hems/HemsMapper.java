package com.helianhealth.agent.mapper.hems;

import org.apache.ibatis.annotations.Select;

/**
 * 体软通用查询方法
 * agent只查体软表不做其他以外的操作
 */
public interface HemsMapper {
    /**
     * 根据就诊编号查询患者编号
     * @param visitNo 就诊编号
     * @return 患者编号
     */
    @Select("SELECT PatientCode as patientCode " +
            "FROM VocaPatient " +
            "WHERE VisitNo = #{visitNo}")
    String selectPatientCodeByVisitNo(String visitNo);

    @Select("select vp.PatientCode as PatientCode " +
            "from VocaPatientFeeItem as vpfi " +
            "inner join VocaPatient as vp on vp.ID_Patient = vpfi.ID_Patient " +
            "where vpfi.applyId = #{applyId}")
    String selectPatientCodeByApplyId(String applyId);
}
