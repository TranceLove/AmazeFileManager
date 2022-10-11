/*
 * Copyright (C) 2014-2022 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.docker

import org.junit.Assert.assertTrue
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * [TestRule] for [IgnoreIfDockerNotPresent].
 */
class IgnoreIfDockerNotPresentRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement {
        return IgnoreIfDockerNotPresentStatement(base, description)
    }

    private class IgnoreIfDockerNotPresentStatement(
        private val base: Statement,
        private val description: Description
    ) :
        Statement() {
        override fun evaluate() {
            Runtime.getRuntime().run {
                val clazz = IgnoreIfDockerNotPresent::class.java
                val hasAnnotation = description.getAnnotation(clazz) != null ||
                    description.testClass.getAnnotation(clazz) != null
                val hasDocker =
                    (
                        hasAnnotation &&
                            exec("docker").waitFor() == 0 && // docker is present
                            exec("docker info").waitFor() == 0 // docker is running
                        )
                assertTrue(
                    "Docker is not present, skipping test ${description.className}",
                    hasDocker
                )
            }
            base.evaluate()
        }
    }
}
