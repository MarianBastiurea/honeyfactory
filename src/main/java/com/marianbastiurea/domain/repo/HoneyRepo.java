package com.marianbastiurea.domain.repo;

import com.marianbastiurea.domain.enums.HoneyType;

import java.math.BigDecimal;

public interface HoneyRepo {
    BigDecimal freeKg(HoneyType type);
}
