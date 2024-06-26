package coco.ide.global.common.exception;

import lombok.Getter;

@Getter
public class BusinessLogicException extends RuntimeException {
    private final ExceptionCode exceptionCode;

    public BusinessLogicException(ExceptionCode exceptionCode) {
        super(exceptionCode.getDescription());
        this.exceptionCode = exceptionCode;
    }

}
