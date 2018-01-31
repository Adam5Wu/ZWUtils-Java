# ZWUtils-Java
[![License](https://img.shields.io/github/license/Adam5Wu/ZWUtils-Java.svg)](./LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/Adam5Wu/ZWUtils-Java.svg)](https://github.com/Adam5Wu/ZWUtils-Java/issues)
[![GitHub forks](https://img.shields.io/github/forks/Adam5Wu/ZWUtils-Java.svg)](https://github.com/Adam5Wu/ZWUtils-Java/network)
[![Build Status](https://travis-ci.org/Adam5Wu/ZWUtils-Java.svg?branch=master)](https://travis-ci.org/Adam5Wu/ZWUtils-Java)
[![SonarCloud-Stat](https://sonarcloud.io/api/badges/gate?key=ZWUtils-Java)](https://sonarcloud.io/dashboard?id=ZWUtils-Java)

[![SonarCloud-SLoC](https://sonarcloud.io/api/badges/measure?key=ZWUtils-Java&metric=lines)](https://sonarcloud.io/dashboard?id=ZWUtils-Java)
[![SonarCloud-Bugs](https://sonarcloud.io/api/badges/measure?key=ZWUtils-Java&metric=bugs)](https://sonarcloud.io/dashboard?id=ZWUtils-Java)
[![SonarCloud-Vuls](https://sonarcloud.io/api/badges/measure?key=ZWUtils-Java&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=ZWUtils-Java)
[![SonarCloud-Smls](https://sonarcloud.io/api/badges/measure?key=ZWUtils-Java&metric=code_smells)](https://sonarcloud.io/dashboard?id=ZWUtils-Java)
[![SonarCloud-Cvge](https://sonarcloud.io/api/badges/measure?key=ZWUtils-Java&metric=coverage)](https://sonarcloud.io/dashboard?id=ZWUtils-Java)

This utility library provides basic run-time services, such as log management, configuration loading and saving, thread management, debugging support, etc. to Java applications.

It does not want to be the "master piece" of any of the services it provides. Instead, it aims at providing essential functionalities with least effort to use -- so that **your code is the master piece of your application**.

The idea of creating this library stems several years back, when I am creating a research prototype application in NEC Laboratories America, Inc. I would like to use some fundamental services for my application, such as log, configuration and thread management, but the stock ones provided in the JDK is too limited in functionality. The alternative, using well-known master piece projects, is a little to "heavy" for me. Those projects are gigantic and versatile, they are excellent at what they do -- if you know them well. **But why do I have to spend days dive into oceans of documentations and online Q&A, and include a library that is 20x~100x larger than my own code, and only satistify one of several basic needs for my application?** *Seriously, why?*

So comes the birth of ZWUtils-Java. It is built on top of existing JDK, and extends the area which I found the stock implementation is weak at. It serves the basic needs for my application, *no frills attached*. Over the years, this library was employed in several more projects, and the services it provides grew through the maintenance and development. But I try to stick to the original principal -- simple to use and gets the job done.

It is far from perfect, and again certainly cannot completely replace any of the master piece service libraries out there. But if it can be a tiny tool jar you keep at hand and gets your garage project done nice and fast, by all means, take it with you! :)

Feel free to issue any questions, problems, ideas you have for this library.
I will be actively maintaining this repository for the forseeable future. :D

# Acknowledgement
I am grateful for NEC Laboratories America, Inc., for granting open source release of this library.

# License
BSD 3-clause New License

# Build Instructions
- [Manual Building and Publishing](BUILD.md)
- [Automated Building and Publishing](BUILD-CI.md)

# Documentation
(Coming soon in wiki)
