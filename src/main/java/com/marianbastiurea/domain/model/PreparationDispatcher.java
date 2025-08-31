package com.marianbastiurea.domain.model;

public interface PreparationDispatcher {
    void dispatch(PrepCommand cmd);
}