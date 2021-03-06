/*
   collabREate proj_info.cpp
   Copyright (C) 2018 Chris Eagle <cseagle at gmail d0t com>
   Copyright (C) 2018 Tim Vidas <tvidas at gmail d0t com>

   This program is free software; you can redistribute it and/or modify it
   under the terms of the GNU General Public License as published by the Free
   Software Foundation; either version 2 of the License, or (at your option)
   any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT
   ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
   FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
   more details.

   You should have received a copy of the GNU General Public License along with
   this program; if not, write to the Free Software Foundation, Inc., 59 Temple
   Place, Suite 330, Boston, MA 02111-1307 USA
 */

#include <string.h>
#include "proj_info.h"

sem_t uidMutex;

Project::Project(uint32_t localpid, const string &description, uint32_t currentlyconnected) {
   lpid = localpid;
   desc = description;
   connected = currentlyconnected;
   parent = 0;
   pdesc = "";
   snapupdateid = 0;
   pub = sub = 0;
   proto = 0;
   hash = "";
   gpid = "";
}

Project::Project(const Project &p) {
   *this = p;
}

BasicProject::BasicProject(uint32_t localpid, const string &description, uint32_t currentlyconnected, uint64_t init_uid) :
         Project(localpid, description, currentlyconnected) {
   updateid = init_uid;
   sem_init(&uidMutex, 0, 1);
}

BasicProject::BasicProject(const BasicProject &bp) {
   *this = bp;
}

BasicProject::~BasicProject() {
   for (vector<char*>::iterator i = updates.begin(); i != updates.end(); i++) {
      free(*i);
   }
}

uint64_t BasicProject::next_uid() {
   uint64_t result;
   sem_wait(&uidMutex);
   result = ++updateid;
   sem_post(&uidMutex);
   return result;
}

void BasicProject::append_update(const char *update) {
   updates.push_back(strdup(update));
}

