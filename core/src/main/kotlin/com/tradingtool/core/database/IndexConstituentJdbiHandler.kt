package com.tradingtool.core.database

import com.tradingtool.core.indexconstituents.dao.IndexConstituentReadDao
import com.tradingtool.core.indexconstituents.dao.IndexConstituentWriteDao

typealias IndexConstituentJdbiHandler = JdbiHandler<IndexConstituentReadDao, IndexConstituentWriteDao>
