// SPDX-FileCopyrightText: 2017-2024 City of Espoo
//
// SPDX-License-Identifier: LGPL-2.1-or-later

// GENERATED FILE: no manual modifications
/* eslint-disable import/order, prettier/prettier, @typescript-eslint/no-namespace, @typescript-eslint/no-redundant-type-constituents */

import { JsonOf } from 'lib-common/json'
import { PlacementType } from 'lib-common/generated/api-types/placement'
import { ServiceNeedOptionPublicInfo } from 'lib-common/generated/api-types/serviceneed'
import { client } from '../../api-client'
import { uri } from 'lib-common/uri'


/**
* Generated from fi.espoo.evaka.serviceneed.ServiceNeedController.getServiceNeedOptionPublicInfos
*/
export async function getServiceNeedOptionPublicInfos(
  request: {
    placementTypes: PlacementType[]
  }
): Promise<ServiceNeedOptionPublicInfo[]> {
  const { data: json } = await client.request<JsonOf<ServiceNeedOptionPublicInfo[]>>({
    url: uri`/public/service-needs/options`.toString(),
    method: 'GET',
    params: {
      placementTypes: request.placementTypes
    }
  })
  return json
}
