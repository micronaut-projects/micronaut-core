import React from 'react';
import VendorCard from "./VendorCard";
import {array} from 'prop-types'

const VendorsTable = ({vendors}) => <div>
  {vendors.map((v, i) => <VendorCard key={i} vendor={v}/>)}
</div>

VendorsTable.propTypes = {
  vendors: array.isRequired
}

export default VendorsTable;
