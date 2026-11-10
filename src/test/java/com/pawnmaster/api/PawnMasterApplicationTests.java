package com.pawnmaster.api;

import com.pawnmaster.api.common.BaseResponse;
import com.pawnmaster.api.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PawnMasterApplicationTests {

    @Test
    void baseResponseShouldBuildCorrectly() {
        BaseResponse<String> response = BaseResponse.ok("PawnMaster Ready");
        assertTrue(response.isSuccess());
        assertEquals(ErrorCode.SUCCESS.getCode(), response.getCode());
        assertEquals("PawnMaster Ready", response.getData());
    }
}

