import React from 'react'
import config from "../config";

const VendorCard = ({vendor}) => <div className="card" style={{width: '18rem', float: 'left', margin: 10}}>
  <img className="card-img-top" src={`${config.SERVER_URL}/images/${vendor.pets.length > 0 ? vendor.pets[0].image : 'missing.png'}`} style={{width: '18rem', height: '18rem', objectFit: 'cover'}} alt={vendor.name} />
    <div className="card-body">
      <h5 className="card-title">{vendor.name}</h5>
      <p className="card-text">Pets: {vendor.pets.length}</p>
      <span className="btn btn-primary">See all Pets</span>
    </div>
</div>;

export default VendorCard