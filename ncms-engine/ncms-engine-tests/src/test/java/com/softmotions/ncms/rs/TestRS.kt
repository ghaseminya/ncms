package com.softmotions.ncms.rs

import com.softmotions.ncms.DbTestsFactory


/**
 * @author Adamansky Anton (adamansky@gmail.com)
 */
class TestRS : DbTestsFactory() {

    override fun createTest(db: String): Array<out Any> {
        return arrayOf(
                _TestAsmRS(db),
                _TestPageRS(db),
                _TestMediaRS(db),
                _TestMttRulesRS(db))
    }
}