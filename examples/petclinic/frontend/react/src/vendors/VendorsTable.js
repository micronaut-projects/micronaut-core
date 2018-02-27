import React from 'react';
import VendorCard from "./VendorCard";

const VendorsTable = ({vendors}) => <div>
  {vendors.map((v, i) => <VendorCard key={i} vendor={v}/>)}
</div>

export default VendorsTable;
