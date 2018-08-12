package com.patson.data

import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map
import com.patson.data.Constants._
import com.patson.model._
import com.patson.MainSimulation
import java.sql.Statement
import java.io.ByteArrayInputStream
import java.sql.Blob
import com.patson.model.AllianceEvent.AllianceEvent


object AllianceSource {
  private[this] val BASE_ALLIANCE_QUERY = "SELECT * FROM " + ALLIANCE_TABLE
  private[this] val BASE_ALLIANCE_MEMBER_QUERY = "SELECT * FROM " + ALLIANCE_MEMBER_TABLE
  private[this] val BASE_ALLIANCE_HISTORY_QUERY = "SELECT * FROM " + ALLIANCE_HISTORY_TABLE
  
  def loadAllAlliances(fullLoad : Boolean = false) = {
      loadAlliancesByCriteria(List.empty, fullLoad)
  }
  
  def loadAlliancesByIds(ids : List[Int], fullLoad : Boolean = false) = {
    if (ids.isEmpty) {
      List.empty
    } else {
      val queryString = new StringBuilder(BASE_ALLIANCE_QUERY + " where id IN (");
      for (i <- 0 until ids.size - 1) {
            queryString.append("?,")
      }
      
      queryString.append("?)")
      loadAlliancesByQueryString(queryString.toString(), ids, fullLoad)
    }
  }
  
  def loadAlliancesByCriteria(criteria : List[(String, Any)], fullLoad : Boolean = false) = {
      var queryString = BASE_ALLIANCE_QUERY
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      loadAlliancesByQueryString(queryString, criteria.map(_._2), fullLoad)
  }
  
  private def loadAlliancesByQueryString(queryString : String, parameters : List[Any], fullLoad : Boolean = false) : scala.collection.immutable.Map[Alliance, List[AllianceMember]] = {
    val connection = Meta.getConnection()
    try {
        val preparedStatement = connection.prepareStatement(queryString)
        
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }
        
        
        val resultSet = preparedStatement.executeQuery()
        
        val alliances = Map[Alliance, List[AllianceMember]]()
        
        while (resultSet.next()) {
          val alliance = Alliance(name = resultSet.getString("name"), status = AllianceStatus.withName(resultSet.getString("status")), creationCycle = resultSet.getInt("creation_cycle"))
          alliance.id = resultSet.getInt("id")
          
          
          alliances.put(alliance, loadAllianceMembersByAlliance(alliance, fullLoad))
        }
        
        resultSet.close()
        preparedStatement.close()
        
        alliances.toMap
      } finally {
        connection.close()
      }
  }

  
  
  private def loadAllianceMembersByAlliance(alliance : Alliance, fullLoad : Boolean = false) : List[AllianceMember] = {
    val connection = Meta.getConnection()
    try {
        val preparedStatement = connection.prepareStatement(BASE_ALLIANCE_MEMBER_QUERY + " WHERE alliance = ? ")
        
        preparedStatement.setObject(1, alliance.id)
        
        val resultSet = preparedStatement.executeQuery()
        
        val allianceMembers = new ListBuffer[AllianceMember]()
        val airlineIds = new ListBuffer[Int]()
        
        while (resultSet.next()) {
          airlineIds.append(resultSet.getInt("airline"))
        }
        
        val airlinesById = AirlineSource.loadAirlinesByIds(airlineIds.toList, fullLoad).map( airline => (airline.id , airline)).toMap
        
        resultSet.beforeFirst()
        while (resultSet.next()) {
          val allianceMember = AllianceMember(alliance, airlinesById(resultSet.getInt("airline")), AllianceRole.withName(resultSet.getString("role")), joinedCycle = resultSet.getInt("joined_cycle"))
          allianceMembers.append(allianceMember)    
        }
        resultSet.close()
        preparedStatement.close()
        
        allianceMembers.toList
      } finally {
        connection.close()
      }
  }
  
  def loadAllianceMemberByAirline(airline : Airline) : Option[AllianceMember] = {
    val connection = Meta.getConnection()
    try {
        val preparedStatement = connection.prepareStatement("SELECT a.*, am.* FROM " + ALLIANCE_TABLE + " a JOIN " + ALLIANCE_MEMBER_TABLE + " am ON a.id = am.alliance WHERE am.airline = ? ")
        
        preparedStatement.setObject(1, airline.id)
        
        val resultSet = preparedStatement.executeQuery()
        
        val result = if (resultSet.next()) {
          val alliance = Alliance(name = resultSet.getString("a.name"), status = AllianceStatus.withName(resultSet.getString("a.status")), creationCycle = resultSet.getInt("a.creation_cycle"), id = resultSet.getInt("a.id"))
          val allianceMember = AllianceMember(alliance, airline, role = AllianceRole.withName(resultSet.getString("am.role")), joinedCycle = resultSet.getInt("am.joined_cycle"))
          Some(allianceMember)
        } else {
          None
        }
        resultSet.close()
        preparedStatement.close()
        
        result
      } finally {
        connection.close()
      }
  }
  
  def loadAllianceHistoryByAirline(airlineId : Int) : List[AllianceHistory] = {
    loadAllianceHistoryByCriteria(List(("airline", airlineId)))
  }
  
  def loadAllianceHistoryByAlliance(allianceId : Int) : List[AllianceHistory] = {
    loadAllianceHistoryByCriteria(List(("alliance", allianceId)))
  }
  
  def loadAllianceHistoryByCriteria(criteria : List[(String, Any)], fullLoad : Boolean = false) = {
      var queryString = BASE_ALLIANCE_HISTORY_QUERY
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      loadAllianceHistoryByQueryString(queryString, criteria.map(_._2), fullLoad)
  }
  
  private def loadAllianceHistoryByQueryString(queryString : String, parameters : List[Any], fullLoad : Boolean = false) : List[AllianceHistory]= {
    val connection = Meta.getConnection()
    try {
        val preparedStatement = connection.prepareStatement(queryString)
        
        for (i <- 0 until parameters.size) {
          preparedStatement.setObject(i + 1, parameters(i))
        }
        
        
        val resultSet = preparedStatement.executeQuery()
        
        val allianceHistoryEntries = ListBuffer[AllianceHistory]()
        
        val airlineIds = new ListBuffer[Int]()
        
        while (resultSet.next()) {
          airlineIds.append(resultSet.getInt("airline"))
        }
        
        val airlinesById = AirlineSource.loadAirlinesByIds(airlineIds.toList, fullLoad).map( airline => (airline.id , airline)).toMap
        
        resultSet.beforeFirst()
        
        while (resultSet.next()) {
          val allianceHistoryEntry = AllianceHistory(allianceName = resultSet.getString("alliance_name"), airline = airlinesById(resultSet.getInt("airline")), event = AllianceEvent.withName(resultSet.getString("status")), cycle = resultSet.getInt("cycle"))
          allianceHistoryEntries.append(allianceHistoryEntry)
        }
        
        resultSet.close()
        preparedStatement.close()
        
        allianceHistoryEntries.toList
      } finally {
        connection.close()
      }
  }
  
 
  
  def saveAlliance(alliance : Alliance) : Int = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("INSERT INTO " + ALLIANCE_TABLE + "(name, status, creation_cycle) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)
          
      preparedStatement.setString(1, alliance.name)
      preparedStatement.setString(2, alliance.status.toString)
      preparedStatement.setInt(3, alliance.creationCycle)
      preparedStatement.executeUpdate()
      val generatedKeys = preparedStatement.getGeneratedKeys
      if (generatedKeys.next()) {
        val generatedId = generatedKeys.getInt(1)
        println("Allaince Id is " + generatedId)
        alliance.id = generatedId
      } 
      
      preparedStatement.close()
      alliance.id
    } finally {
      connection.close()
    }
  }
  
  def updateAlliance(alliance : Alliance) = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("UPDATE " + ALLIANCE_TABLE + " SET status = ? WHERE id = ?")
          
      preparedStatement.setString(1, alliance.status.toString)
      preparedStatement.setInt(2, alliance.id)
      preparedStatement.executeUpdate()
      preparedStatement.close()
    } finally {
      connection.close()
    }
  }

  def deleteAlliance(allianceId : Int) = {
    deleteAllianceByCriteria(List(("id", allianceId)))
  }

  
  def deleteAllianceByCriteria(criteria : List[(String, Any)]) = {
      //open the hsqldb
    val connection = Meta.getConnection()
    try {
      var queryString = "DELETE FROM " + ALLIANCE_TABLE
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      
      val preparedStatement = connection.prepareStatement(queryString)
      
      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._2)
      }
      
      val deletedCount = preparedStatement.executeUpdate()
      
      preparedStatement.close()
      println("Deleted " + deletedCount + " airline alliance records")
      deletedCount
      
    } finally {
      connection.close()
    }
  }
  
  def saveAllianceMember(allianceMember : AllianceMember) = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("REPLACE INTO " + ALLIANCE_MEMBER_TABLE + "(alliance, airline, role, joined_cycle) VALUES(?,?,?,?)")
          
      preparedStatement.setInt(1, allianceMember.alliance.id)
      preparedStatement.setInt(2, allianceMember.airline.id)
      preparedStatement.setString(3, allianceMember.role.toString)
      preparedStatement.setInt(4, allianceMember.joinedCycle)
      preparedStatement.executeUpdate()
      preparedStatement.close()
      
    } finally {
      connection.close()
    }
  }
 

  def deleteAllianceMember(airlineId : Int) = {
    deleteAllianceMemberByCriteria(List(("airline", airlineId)))
  }

  
  def deleteAllianceMemberByCriteria(criteria : List[(String, Any)]) = {
      //open the hsqldb
    val connection = Meta.getConnection()
    try {
      var queryString = "DELETE FROM " + ALLIANCE_MEMBER_TABLE
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      
      val preparedStatement = connection.prepareStatement(queryString)
      
      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._2)
      }
      
      val deletedCount = preparedStatement.executeUpdate()
      
      preparedStatement.close()
      println("Deleted " + deletedCount + " airline alliance member records")
      deletedCount
      
    } finally {
      connection.close()
    }
  }
  
    
  def saveAllianceHistory(allianceHistory : AllianceHistory) : Int = {
    val connection = Meta.getConnection()
    try {
      val preparedStatement = connection.prepareStatement("INSERT INTO " + ALLIANCE_HISTORY_TABLE + "(alliance_name, airline, event, cycle) VALUES(?,?,?,?)",  Statement.RETURN_GENERATED_KEYS)
          
      preparedStatement.setString(1, allianceHistory.allianceName)
      preparedStatement.setInt(2, allianceHistory.airline.id)
      preparedStatement.setString(3, allianceHistory.event.toString)
      preparedStatement.setInt(4, allianceHistory.cycle)
      preparedStatement.executeUpdate()
      
      val generatedKeys = preparedStatement.getGeneratedKeys
      generatedKeys.next
      val generatedId = generatedKeys.getInt(1)
        
      preparedStatement.close()
      
      generatedId
    } finally {
      connection.close()
    }
  }
 

  
  
  def deleteAllianceHistoryByCriteria(criteria : List[(String, Any)]) = {
      //open the hsqldb
    val connection = Meta.getConnection()
    try {
      var queryString = "DELETE FROM " + ALLIANCE_HISTORY_TABLE
      
      if (!criteria.isEmpty) {
        queryString += " WHERE "
        for (i <- 0 until criteria.size - 1) {
          queryString += criteria(i)._1 + " = ? AND "
        }
        queryString += criteria.last._1 + " = ?"
      }
      
      val preparedStatement = connection.prepareStatement(queryString)
      
      for (i <- 0 until criteria.size) {
        preparedStatement.setObject(i + 1, criteria(i)._2)
      }
      
      val deletedCount = preparedStatement.executeUpdate()
      
      preparedStatement.close()
      println("Deleted " + deletedCount + " airline history records")
      deletedCount
    } finally {
      connection.close()
    }
  }
}