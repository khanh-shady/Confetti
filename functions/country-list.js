'use strict'

var data = require('./country-list.json')

/** Precompute name and code lookups. */
var nameMap = {}
var codeMap = {}
data.forEach(mapCodeAndName)

function mapCodeAndName (country) {
  nameMap[country.name.toLowerCase()] = country.code
  codeMap[country.code.toLowerCase()] = country.name
}

exports.overwrite = function overwrite (countries) {
  if (!countries || !countries.length) return
  countries.forEach((country) => {
    var foundIndex = data.findIndex((item) => {
      return item.code === country.code
    })
    data[foundIndex] = country
    mapCodeAndName(country)
  })
}

exports.getCode = function getCode (name) {
  return nameMap[name.toLowerCase()]
}

exports.getName = function getName (code) {
  return codeMap[code.toLowerCase()]
}

exports.getNames = function getNames () {
  return data.map((country) => {
    return country.name
  })
}

exports.getCodes = function getCodes () {
  return data.map((country) => {
    return country.code
  })
}

exports.getCodeList = function getCodeList () {
  return codeMap
}

exports.getNameList = function getNameList () {
  return nameMap
}

exports.getData = function getData () {
  return data
}