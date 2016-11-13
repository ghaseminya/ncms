package com.softmotions.ncms.security;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * @author Adamansky Anton (adamansky@gmail.com)
 */
public class NcmsSecurityModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NcmsSecurityRS.class).in(Singleton.class);
    }
}
