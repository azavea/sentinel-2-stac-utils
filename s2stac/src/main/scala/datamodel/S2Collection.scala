package com.azavea.s2stac.datamodel

sealed abstract class S2Collection(val repr: String)

case object L1C extends S2Collection("L1C")
case object L2A extends S2Collection("L2A")
