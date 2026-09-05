package com.ygq.feedly.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(CodeMsg codeMsg) {
        super(codeMsg.getMsg());
        this.code = codeMsg.getCode();
    }

    public BusinessException(CodeMsg codeMsg, String message) {
        super(message);
        this.code = codeMsg.getCode();
    }

    public BusinessException(CodeMsg codeMsg, Throwable cause) {
        super(codeMsg.getMsg(), cause);
        this.code = codeMsg.getCode();
    }
}