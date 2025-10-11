package com.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AosApplication

fun main(args: Array<String>) {
    runApplication<AosApplication>(*args)
}
